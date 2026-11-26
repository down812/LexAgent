package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.PromptConstant;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;
import org.lexagent.model.vo.LegalAnalysisResult.LegalBasisItem;
import org.lexagent.model.vo.ReviewResult;
import org.lexagent.service.agent.ReviewerAgentService;
import org.lexagent.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 提取阿拉伯数字 */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    @Override
    public ReviewResult review(CaseAnalysisResult caseAnalysis,
                               LegalAnalysisResult legalAnalysis,
                               List<HybridSearchResult> searchResults) {
        return review(caseAnalysis, legalAnalysis, searchResults, null, null);
    }

    @Override
    public ReviewResult review(CaseAnalysisResult caseAnalysis,
                               LegalAnalysisResult legalAnalysis,
                               List<HybridSearchResult> searchResults,
                               org.lexagent.model.vo.CaseContextVO caseContext,
                               org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult) {
        log.info("[Reviewer] 开始审核，legalBasis={}, searchResults={}, withEvidence={}",
                legalAnalysis != null && legalAnalysis.getLegalBasis() != null
                        ? legalAnalysis.getLegalBasis().size() : 0,
                searchResults != null ? searchResults.size() : 0,
                caseContext != null);
        long start = System.currentTimeMillis();

        // 诊断日志：打印检索结果和法律依据的原始值，便于定位匹配问题
        if (searchResults != null) {
            for (HybridSearchResult r : searchResults) {
                log.info("[Reviewer诊断] 检索结果: lawName=[{}], articleNo=[{}], verified={}",
                        r.getLawName(), r.getArticleNo(), r.getVerified());
            }
        }
        if (legalAnalysis != null && legalAnalysis.getLegalBasis() != null) {
            for (LegalBasisItem item : legalAnalysis.getLegalBasis()) {
                log.info("[Reviewer诊断] 法律依据: lawName=[{}], articleNo=[{}]",
                        item.getLawName(), item.getArticleNo());
            }
        }
        log.info("[Reviewer诊断] LLM可用: {}", llmService.isAvailable());

        // ========== 1. 算法预检：法律依据真实性 ==========
        List<ReviewResult.ReviewIssue> algorithmIssues = new ArrayList<>();
        int basisTotal = (legalAnalysis != null && legalAnalysis.getLegalBasis() != null)
                ? legalAnalysis.getLegalBasis().size() : 0;
        int basisCheckPass = checkLegalBasis(legalAnalysis, searchResults, algorithmIssues);

        // ========== 2. 算法预检：风险表达检测 ==========
        checkRiskExpression(legalAnalysis, algorithmIssues);

        // ========== 2.5 算法预检：证据不足未说明（V2.0 AGENT-003 检查项 f） ==========
        checkEvidenceInsufficientDisclosure(legalAnalysis, caseContext, evidenceResult, algorithmIssues);

        // ========== 3. LLM 深度审核（有案件证据输入时启用证据核验模板 a-f） ==========
        ReviewResult llmResult = null;
        if (llmService.isAvailable()) {
            llmResult = llmReview(caseAnalysis, legalAnalysis, searchResults, caseContext, evidenceResult);
        }

        // ========== 4. 综合审核结果 ==========
        ReviewResult finalResult = mergeResults(algorithmIssues, basisCheckPass, basisTotal, llmResult);

        long cost = System.currentTimeMillis() - start;
        log.info("[Reviewer] 审核完成：passed={}, confidence={}, issues={}, 耗时={}ms",
                finalResult.getPassed(), finalResult.getConfidenceScore(),
                finalResult.getIssues() != null ? finalResult.getIssues().size() : 0, cost);
        return finalResult;
    }

    /**
     * 算法预检1：法律依据真实性
     * <p>
     * 检查 LLM 分析中引用的法律名+条款号是否来自检索结果。
     * 采用归一化匹配：法律名去书名号/去"中华人民共和国"前缀后双向 contains；
     * 条款号提取数字（中文数字转阿拉伯）后按数字比较。
     *
     * @return 通过校验的条款数
     */
    private int checkLegalBasis(LegalAnalysisResult legalAnalysis,
                                List<HybridSearchResult> searchResults,
                                List<ReviewResult.ReviewIssue> issues) {
        if (legalAnalysis == null || legalAnalysis.getLegalBasis() == null
                || legalAnalysis.getLegalBasis().isEmpty()) {
            // 未引用法律依据，不视为问题（由 LLM 审核判断分析是否充分）
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

        int total = legalAnalysis.getLegalBasis().size();
        int passed = 0;
        List<LegalBasisItem> unmatched = new ArrayList<>();

        for (LegalBasisItem item : legalAnalysis.getLegalBasis()) {
            boolean matched = matchAnyBasis(item, searchResults);
            log.info("[Reviewer诊断] 匹配结果: 《{}》{} -> {}",
                    item.getLawName(), item.getArticleNo(), matched ? "命中" : "未命中");
            if (matched) {
                passed++;
            } else {
                unmatched.add(item);
            }
        }
        log.info("[Reviewer诊断] 法律依据校验: passed={}/{}", passed, total);

        // 根据通过率决定不匹配条款的严重度：
        // - 通过率 >= 50%：不匹配项可能仅是格式差异，降为 MEDIUM
        // - 通过率 < 50% 且 > 0：部分匹配，不匹配项为 HIGH
        // - 全部不匹配：HIGH（疑似整体编造）
        double passRate = total == 0 ? 0 : (double) passed / total;
        String severity = passRate >= 0.5 ? "MEDIUM" : "HIGH";

        for (LegalBasisItem item : unmatched) {
            String desc = "法律分析引用的《" + StrUtil.blankToDefault(item.getLawName(), "?")
                    + "》" + StrUtil.blankToDefault(item.getArticleNo(), "?")
                    + "未在检索结果中找到精确匹配";
            if (severity.equals("MEDIUM")) {
                desc += "（可能为格式差异，建议核实）";
            } else {
                desc += "，疑似编造条款";
            }
            issues.add(ReviewResult.ReviewIssue.builder()
                    .type("LEGAL_BASIS")
                    .severity(severity)
                    .description(desc)
                    .suggestion(severity.equals("HIGH")
                            ? "移除该引用或重新检索确认" : "核实法律名称与条款编号是否与检索结果一致")
                    .build());
        }
        return passed;
    }

    /**
     * 判断单条法律依据是否匹配任一检索结果（法律名 + 条款号双重匹配）
     */
    private boolean matchAnyBasis(LegalBasisItem item, List<HybridSearchResult> results) {
        String itemLaw = item.getLawName();
        String itemArticle = item.getArticleNo();
        if (StrUtil.isBlank(itemLaw) && StrUtil.isBlank(itemArticle)) {
            return false;
        }
        for (HybridSearchResult r : results) {
            if (lawNameMatch(itemLaw, r.getLawName())
                    && articleMatch(itemArticle, r.getArticleNo())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 法律名匹配：归一化后双向 contains
     * 归一化 = 去书名号《》、去"中华人民共和国"前缀、去空白
     */
    private boolean lawNameMatch(String a, String b) {
        String na = normalizeLawName(a);
        String nb = normalizeLawName(b);
        if (na.isEmpty() || nb.isEmpty()) {
            // 法律名为空时，仅靠条款号匹配（返回 true 放行，由条款号决定）
            return true;
        }
        return na.contains(nb) || nb.contains(na);
    }

    /**
     * 条款号匹配：优先提取数字比较，退化为字符串 contains
     */
    private boolean articleMatch(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) {
            // 条款号为空时，仅靠法律名匹配
            return true;
        }
        int numA = extractArticleNumber(a);
        int numB = extractArticleNumber(b);
        if (numA > 0 && numB > 0) {
            return numA == numB;
        }
        // 无法提取数字时退化为字符串包含
        return a.contains(b) || b.contains(a);
    }

    /**
     * 法律名归一化：去书名号、去"中华人民共和国"前缀、去空白
     */
    private String normalizeLawName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        return name.replace("《", "")
                .replace("》", "")
                .replace("中华人民共和国", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * 从条款号提取数字：支持阿拉伯数字和中文数字
     * 如 "第四十七条"→47，"第47条"→47，"47"→47，"第一百条"→100
     */
    private int extractArticleNumber(String articleNo) {
        if (StrUtil.isBlank(articleNo)) {
            return 0;
        }
        // 先尝试提取阿拉伯数字
        Matcher m = DIGIT_PATTERN.matcher(articleNo);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (Exception e) {
                // 忽略
            }
        }
        // 中文数字转换
        return chineseNumToInt(articleNo);
    }

    /** 中文数字字符映射 */
    private static final Map<Character, Integer> CN_NUM = new HashMap<>();
    static {
        CN_NUM.put('零', 0); CN_NUM.put('一', 1); CN_NUM.put('二', 2);
        CN_NUM.put('三', 3); CN_NUM.put('四', 4); CN_NUM.put('五', 5);
        CN_NUM.put('六', 6); CN_NUM.put('七', 7); CN_NUM.put('八', 8);
        CN_NUM.put('九', 9); CN_NUM.put('十', 10); CN_NUM.put('百', 100);
        CN_NUM.put('千', 1000); CN_NUM.put('两', 2);
    }

    /**
     * 简单中文数字转整数（支持到千）
     * 如 "四十七"→47，"二十一"→21，"十"→10，"一百"→100，"一百零八"→108
     */
    private int chineseNumToInt(String s) {
        int result = 0;
        int current = 0;
        for (char c : s.toCharArray()) {
            Integer v = CN_NUM.get(c);
            if (v == null) {
                continue;
            }
            if (v >= 10) {
                // 十、百、千是单位
                if (current == 0) {
                    current = 1;
                }
                result += current * v;
                current = 0;
            } else {
                current = v;
            }
        }
        result += current;
        return result;
    }

    /**
     * 算法预检2：风险表达检测
     * 扫描分析文本中的绝对化/承诺性表述。
     */
    private void checkRiskExpression(LegalAnalysisResult legalAnalysis,
                                    List<ReviewResult.ReviewIssue> issues) {
        if (legalAnalysis == null) {
            return;
        }
        String text = buildFullText(legalAnalysis);
        if (StrUtil.isBlank(text)) {
            return;
        }

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

    /**
     * 算法预检（V2.0 AGENT-003 检查项 f）：证据不足未说明
     * 当证据分析发现缺失证据，而法律分析完全未提及证据不足相关表述时，给出 MEDIUM 问题。
     */
    private void checkEvidenceInsufficientDisclosure(LegalAnalysisResult legalAnalysis,
                                                     org.lexagent.model.vo.CaseContextVO caseContext,
                                                     org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult,
                                                     List<ReviewResult.ReviewIssue> issues) {
        if (evidenceResult == null || evidenceResult.getMissingEvidence() == null
                || evidenceResult.getMissingEvidence().isEmpty()
                || legalAnalysis == null || caseContext == null) {
            return;
        }
        String text = buildFullText(legalAnalysis);
        if (StrUtil.isBlank(text)) {
            return;
        }
        boolean disclosed = text.contains("证据不足") || text.contains("缺少")
                || text.contains("尚未") || text.contains("建议补充")
                || text.contains("需要补充") || text.contains("暂无");
        if (!disclosed) {
            issues.add(ReviewResult.ReviewIssue.builder()
                    .type("EVIDENCE")
                    .severity("MEDIUM")
                    .description("证据分析发现存在缺失证据（"
                            + String.join("、", evidenceResult.getMissingEvidence().subList(0,
                                    Math.min(2, evidenceResult.getMissingEvidence().size()))) + " 等），"
                            + "但法律分析未对证据不足情况作出说明")
                    .suggestion("在分析中明确说明证据不足之处及其对结论的影响，避免让用户误以为结论已有充分证据支撑")
                    .build());
        }
    }

    private String buildFullText(LegalAnalysisResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.getCaseAnalysis() != null) {
            sb.append(r.getCaseAnalysis());
        }
        if (r.getLegalIssues() != null) {
            for (String s : r.getLegalIssues()) {
                sb.append(s);
            }
        }
        if (r.getSuggestions() != null) {
            for (String s : r.getSuggestions()) {
                sb.append(s);
            }
        }
        return sb.toString();
    }

    /**
     * LLM 深度审核：条款匹配性、事实一致性
     * V2.0：带案件证据信息时使用证据核验模板（AGENT-003 检查项 a-f）
     */
    private ReviewResult llmReview(CaseAnalysisResult caseAnalysis,
                                  LegalAnalysisResult legalAnalysis,
                                  List<HybridSearchResult> searchResults,
                                  org.lexagent.model.vo.CaseContextVO caseContext,
                                  org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult) {
        try {
            String caseJson = JSONUtil.toJsonStr(caseAnalysis);
            String analysisJson = JSONUtil.toJsonStr(legalAnalysis);
            String basisJson = JSONUtil.toJsonStr(searchResults);
            String userPrompt;
            if (caseContext != null) {
                userPrompt = PromptConstant.REVIEWER_WITH_EVIDENCE_USER_TEMPLATE.formatted(
                        caseJson, analysisJson, basisJson,
                        JSONUtil.toJsonStr(caseContext.getEvidences()),
                        JSONUtil.toJsonStr(evidenceResult));
            } else {
                userPrompt = String.format(PromptConstant.REVIEWER_USER_TEMPLATE,
                        caseJson, analysisJson, basisJson);
            }

            String llmResponse = llmService.chat(PromptConstant.REVIEWER_SYSTEM, userPrompt);
            String json = llmService.extractJson(llmResponse);
            ReviewResult result = JSONUtil.toBean(json, ReviewResult.class);
            log.info("[Reviewer诊断] LLM审核返回: confidence={}, passed={}, issues={}",
                    result.getConfidenceScore(), result.getPassed(),
                    result.getIssues() != null ? result.getIssues().size() : 0);
            return result;
        } catch (Exception e) {
            log.warn("[Reviewer] LLM 审核失败，仅使用算法预检结果：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 综合算法预检与 LLM 审核结果
     */
    private ReviewResult mergeResults(List<ReviewResult.ReviewIssue> algorithmIssues,
                                      int basisCheckPass, int basisTotal,
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
        long algoHigh = algorithmIssues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
        long llmHigh = (llmResult != null && llmResult.getIssues() != null)
                ? llmResult.getIssues().stream().filter(i -> "HIGH".equals(i.getSeverity())).count() : 0;
        log.info("[Reviewer诊断] hasHigh={}, 来源: 算法HIGH×{}, LLM_HIGH×{}", hasHigh, algoHigh, llmHigh);

        // 计算综合可信度
        int confidence = calculateConfidence(allIssues, basisCheckPass, basisTotal, llmResult);

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
     * 基础分 85，HIGH 问题 -15，MEDIUM 问题 -5，LOW 问题 -2
     * 法律依据通过率加权（高通过率加分，低通过率扣分）
     * LLM 评分作为参考（权重 0.3，避免 LLM 保守评分过度拉低算法分）
     */
    private int calculateConfidence(List<ReviewResult.ReviewIssue> issues,
                                    int basisCheckPass, int basisTotal,
                                    ReviewResult llmResult) {
        int score = 85;
        int highCount = 0, mediumCount = 0, lowCount = 0;

        // 问题扣分
        for (ReviewResult.ReviewIssue issue : issues) {
            String sev = issue.getSeverity();
            if ("HIGH".equals(sev)) {
                score -= 15;
                highCount++;
            } else if ("MEDIUM".equals(sev)) {
                score -= 5;
                mediumCount++;
            } else if ("LOW".equals(sev)) {
                score -= 2;
                lowCount++;
            }
        }

        // 法律依据通过率加权
        int passRateBonus = 0;
        if (basisTotal > 0) {
            double passRate = (double) basisCheckPass / basisTotal;
            if (passRate >= 0.8) {
                score += 5;  // 高通过率奖励
                passRateBonus = 5;
            } else if (passRate < 0.3) {
                score -= 5;  // 低通过率额外扣分
                passRateBonus = -5;
            }
        }

        int algoScore = score;
        // LLM 评分作为参考（权重 0.3）
        if (llmResult != null && llmResult.getConfidenceScore() != null) {
            score = (int) Math.round(score * 0.7 + llmResult.getConfidenceScore() * 0.3);
            log.info("[Reviewer诊断] 评分计算: 基础85, HIGH×{}(-15), MEDIUM×{}(-5), LOW×{}(-2), 通过率加权({}, pass={}/{}), 算法分={}, LLM分={}, 权重0.7/0.3 -> 最终={}",
                    highCount, mediumCount, lowCount, passRateBonus, basisCheckPass, basisTotal,
                    algoScore, llmResult.getConfidenceScore(), score);
        } else {
            log.info("[Reviewer诊断] 评分计算: 基础85, HIGH×{}(-15), MEDIUM×{}(-5), LOW×{}(-2), 通过率加权({}, pass={}/{}), 算法分={}, 无LLM评分加权 -> 最终={}",
                    highCount, mediumCount, lowCount, passRateBonus, basisCheckPass, basisTotal,
                    algoScore, score);
        }

        return Math.max(0, Math.min(100, score));
    }
}
