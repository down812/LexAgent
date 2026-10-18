package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.PromptConstant;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;
import org.lexagent.model.vo.ReviewResult;
import org.lexagent.service.agent.ReviewerAgentService;
import org.lexagent.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reviewer Agent 实现
 * <p>
 * 双重审核机制：
 * 1. 算法预检（不依赖 LLM）：
 *    - 法律依据真实性：LLM 引用的法律名+条款号是否在检索结果中（防幻觉核心）
 *    - 绝对化表达检测：扫描分析文本中的"一定""必定""绝对"等违规表述
 * 2. LLM 深度审核：条款匹配性、事实一致性等需要语义理解的部分
 * <p>
 * 最终可信度 = 算法预检结果与 LLM 审核结果综合。
 */
@Slf4j
@Service
public class ReviewerAgentServiceImpl implements ReviewerAgentService {

    @Autowired
    private LlmService llmService;

    /** 绝对化/承诺性表述黑名单 */
    private static final String[] RISK_EXPRESSIONS = {
            "一定胜诉", "必定胜诉", "绝对胜诉", "一定败诉", "必定败诉",
            "一定违法", "必定违法", "绝对违法", "一定合法", "必定合法",
            "一定获得赔偿", "必定获得赔偿", "一定能", "必定能", "绝对能",
            "一定不", "必定不", "保证", "承诺", "百分之百"
    };

    @Override
    public ReviewResult review(CaseAnalysisResult caseAnalysis,
                               LegalAnalysisResult legalAnalysis,
                               List<HybridSearchResult> searchResults) {
        log.info("[Reviewer] 开始审核，legalBasis={}, searchResults={}",
                legalAnalysis != null && legalAnalysis.getLegalBasis() != null
                        ? legalAnalysis.getLegalBasis().size() : 0,
                searchResults != null ? searchResults.size() : 0);
        long start = System.currentTimeMillis();

        // ========== 1. 算法预检：法律依据真实性 ==========
        List<ReviewResult.ReviewIssue> algorithmIssues = new ArrayList<>();
        int basisCheckPass = checkLegalBasis(legalAnalysis, searchResults, algorithmIssues);

        // ========== 2. 算法预检：风险表达检测 ==========
        checkRiskExpression(legalAnalysis, algorithmIssues);

        // ========== 3. LLM 深度审核 ==========
        ReviewResult llmResult = null;
        if (llmService.isAvailable()) {
            llmResult = llmReview(caseAnalysis, legalAnalysis, searchResults);
        }

        // ========== 4. 综合审核结果 ==========
        ReviewResult finalResult = mergeResults(algorithmIssues, basisCheckPass, llmResult);

        long cost = System.currentTimeMillis() - start;
        log.info("[Reviewer] 审核完成：passed={}, confidence={}, issues={}, 耗时={}ms",
                finalResult.getPassed(), finalResult.getConfidenceScore(),
                finalResult.getIssues() != null ? finalResult.getIssues().size() : 0, cost);
        return finalResult;
    }

    /**
     * 算法预检1：法律依据真实性
     * 检查 LLM 分析中引用的法律名+条款号是否全部来自检索结果。
     * 若引用了检索结果中不存在的条款，标记为 HIGH 严重度幻觉。
     *
     * @return 通过校验的条款数
     */
    private int checkLegalBasis(LegalAnalysisResult legalAnalysis,
                                List<HybridSearchResult> searchResults,
                                List<ReviewResult.ReviewIssue> issues) {
        if (legalAnalysis == null || legalAnalysis.getLegalBasis() == null
                || legalAnalysis.getLegalBasis().isEmpty()) {
            return 0;
        }
        if (searchResults == null || searchResults.isEmpty()) {
            // 分析引用了法律依据但检索结果为空，疑似编造
            issues.add(ReviewResult.ReviewIssue.builder()
                    .type("LEGAL_BASIS")
                    .severity("HIGH")
                    .description("法律分析引用了法律依据，但检索结果为空，疑似编造条款")
                    .suggestion("请重新检索或移除无依据的法律引用")
                    .build());
            return 0;
        }

        // 构建检索结果的法律名+条款号索引
        Set<String> basisIndex = new HashSet<>();
        for (HybridSearchResult r : searchResults) {
            if (StrUtil.isNotBlank(r.getLawName()) && StrUtil.isNotBlank(r.getArticleNo())) {
                basisIndex.add(r.getLawName() + "|" + r.getArticleNo());
            }
        }

        int passed = 0;
        for (LegalAnalysisResult.LegalBasisItem item : legalAnalysis.getLegalBasis()) {
            String key = StrUtil.blankToDefault(item.getLawName(), "") + "|"
                    + StrUtil.blankToDefault(item.getArticleNo(), "");
            if (basisIndex.contains(key)) {
                passed++;
            } else {
                // 模糊匹配：法律名相同、条款号在检索结果中存在
                boolean fuzzy = false;
                for (HybridSearchResult r : searchResults) {
                    if (StrUtil.isNotBlank(item.getLawName())
                            && item.getLawName().contains(r.getLawName())
                            && StrUtil.isNotBlank(item.getArticleNo())
                            && item.getArticleNo().contains(r.getArticleNo())) {
                        fuzzy = true;
                        break;
                    }
                }
                if (fuzzy) {
                    passed++;
                } else {
                    issues.add(ReviewResult.ReviewIssue.builder()
                            .type("LEGAL_BASIS")
                            .severity("HIGH")
                            .description("法律分析引用的《" + item.getLawName()
                                    + "》" + item.getArticleNo()
                                    + "不在检索结果中，疑似编造条款")
                            .suggestion("移除该引用或重新检索确认")
                            .build());
                }
            }
        }
        return passed;
    }

    /**
     * 算法预检2：风险表达检测
     * 扫描分析文本中的绝对化/承诺性表述。
     */
    private void checkRiskExpression(LegalAnalysisResult legalAnalysis,
                                    List<ReviewResult.ReviewIssue> issues) {
        if (legalAnalysis == null) return;
        String text = buildFullText(legalAnalysis);
        if (StrUtil.isBlank(text)) return;

        List<String> found = new ArrayList<>();
        for (String expr : RISK_EXPRESSIONS) {
            if (text.contains(expr)) {
                found.add(expr);
            }
        }
        if (!found.isEmpty()) {
            issues.add(ReviewResult.ReviewIssue.builder()
                    .type("RISK_EXPRESSION")
                    .severity("MEDIUM")
                    .description("法律分析中使用了绝对化表述：" + String.join("、", found))
                    .suggestion("将绝对化表述改为审慎表达，如'可能涉及''具体结果需结合完整事实判断'")
                    .build());
        }
    }

    private String buildFullText(LegalAnalysisResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.getCaseAnalysis() != null) sb.append(r.getCaseAnalysis());
        if (r.getLegalIssues() != null) {
            for (String s : r.getLegalIssues()) sb.append(s);
        }
        if (r.getSuggestions() != null) {
            for (String s : r.getSuggestions()) sb.append(s);
        }
        return sb.toString();
    }

    /**
     * LLM 深度审核：条款匹配性、事实一致性
     */
    private ReviewResult llmReview(CaseAnalysisResult caseAnalysis,
                                  LegalAnalysisResult legalAnalysis,
                                  List<HybridSearchResult> searchResults) {
        try {
            String caseJson = JSONUtil.toJsonStr(caseAnalysis);
            String analysisJson = JSONUtil.toJsonStr(legalAnalysis);
            String basisJson = JSONUtil.toJsonStr(searchResults);
            String userPrompt = String.format(PromptConstant.REVIEWER_USER_TEMPLATE,
                    caseJson, analysisJson, basisJson);

            String llmResponse = llmService.chat(PromptConstant.REVIEWER_SYSTEM, userPrompt);
            String json = llmService.extractJson(llmResponse);
            return JSONUtil.toBean(json, ReviewResult.class);
        } catch (Exception e) {
            log.warn("[Reviewer] LLM 审核失败，仅使用算法预检结果：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 综合算法预检与 LLM 审核结果
     */
    private ReviewResult mergeResults(List<ReviewResult.ReviewIssue> algorithmIssues,
                                      int basisCheckPass,
                                      ReviewResult llmResult) {
        List<ReviewResult.ReviewIssue> allIssues = new ArrayList<>(algorithmIssues);

        // 合并 LLM 发现的问题（去重）
        if (llmResult != null && llmResult.getIssues() != null) {
            Set<String> existing = new HashSet<>();
            for (ReviewResult.ReviewIssue i : allIssues) {
                existing.add(i.getType() + "|" + i.getDescription());
            }
            for (ReviewResult.ReviewIssue i : llmResult.getIssues()) {
                String key = i.getType() + "|" + i.getDescription();
                if (!existing.contains(key)) {
                    allIssues.add(i);
                }
            }
        }

        // 是否有 HIGH 严重度问题
        boolean hasHigh = allIssues.stream().anyMatch(i -> "HIGH".equals(i.getSeverity()));

        // 计算综合可信度
        int confidence = calculateConfidence(algorithmIssues, basisCheckPass, llmResult, hasHigh);

        // 通过条件：无 HIGH 问题 + 可信度 >= 60
        boolean passed = !hasHigh && confidence >= 60;

        String overallComment = llmResult != null && StrUtil.isNotBlank(llmResult.getOverallComment())
                ? llmResult.getOverallComment()
                : (passed ? "审核通过，法律分析可信度符合要求" : "审核未通过，存在需要修正的问题");

        return ReviewResult.builder()
                .passed(passed)
                .issues(allIssues)
                .overallComment(overallComment)
                .confidenceScore(confidence)
                .build();
    }

    /**
     * 综合可信度计算：
     * 基础分 80，HIGH 问题 -20，MEDIUM 问题 -8，LOW 问题 -3
     * 法律依据校验通过率加权
     */
    private int calculateConfidence(List<ReviewResult.ReviewIssue> issues,
                                     int basisCheckPass,
                                     ReviewResult llmResult,
                                     boolean hasHigh) {
        int score = 80;
        for (ReviewResult.ReviewIssue issue : issues) {
            String sev = issue.getSeverity();
            if ("HIGH".equals(sev)) score -= 20;
            else if ("MEDIUM".equals(sev)) score -= 8;
            else if ("LOW".equals(sev)) score -= 3;
        }
        // LLM 评分作为参考（取加权平均）
        if (llmResult != null && llmResult.getConfidenceScore() != null) {
            score = (score + llmResult.getConfidenceScore()) / 2;
        }
        return Math.max(0, Math.min(100, score));
    }
}
