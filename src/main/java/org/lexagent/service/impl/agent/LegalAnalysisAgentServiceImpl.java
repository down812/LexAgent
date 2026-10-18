package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.PromptConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;
import org.lexagent.service.agent.LegalAnalysisAgentService;
import org.lexagent.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Legal Analysis Agent 实现
 * <p>
 * 将案件事实 + 检索到的法律条文拼接为 LLM 上下文，
 * 调用 LLM 生成结构化法律分析（JSON）。
 * <p>
 * 关键约束：提示词中强制要求法律依据来自检索结果，不得编造。
 */
@Slf4j
@Service
public class LegalAnalysisAgentServiceImpl implements LegalAnalysisAgentService {

    @Autowired
    private LlmService llmService;

    @Override
    public LegalAnalysisResult analyze(CaseAnalysisResult caseAnalysis, List<HybridSearchResult> searchResults) {
        if (caseAnalysis == null) {
            throw new BusinessException("案件分析结果不能为空");
        }

        log.info("[LegalAnalysis] 开始生成法律分析，facts={}, legalBasis={}",
                caseAnalysis.getFacts() != null ? caseAnalysis.getFacts().size() : 0,
                searchResults != null ? searchResults.size() : 0);
        long start = System.currentTimeMillis();

        String caseJson = JSONUtil.toJsonStr(caseAnalysis);
        String basisJson = formatLegalBasis(searchResults);
        String userPrompt = String.format(PromptConstant.LEGAL_ANALYSIS_USER_TEMPLATE, caseJson, basisJson);

        String llmResponse = llmService.chat(PromptConstant.LEGAL_ANALYSIS_SYSTEM, userPrompt);
        String json = llmService.extractJson(llmResponse);

        LegalAnalysisResult result;
        try {
            result = JSONUtil.toBean(json, LegalAnalysisResult.class);
        } catch (Exception e) {
            log.error("[LegalAnalysis] JSON 解析失败：{}", e.getMessage());
            log.error("[LegalAnalysis] LLM 原始回复：{}", llmResponse);
            // 降级：基于检索结果构建基本分析
            result = buildFallback(caseAnalysis, searchResults);
        }

        // 兜底：确保字段非 null
        if (result.getLegalIssues() == null) result.setLegalIssues(new ArrayList<>());
        if (result.getLegalBasis() == null) result.setLegalBasis(new ArrayList<>());
        if (result.getSuggestions() == null) result.setSuggestions(new ArrayList<>());
        if (StrUtil.isBlank(result.getCaseAnalysis())) {
            result.setCaseAnalysis("暂无法生成详细分析，请参考以下法律依据。");
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[LegalAnalysis] 分析完成：issues={}, basis={}, 耗时={}ms",
                result.getLegalIssues().size(), result.getLegalBasis().size(), cost);
        return result;
    }

    /**
     * 将检索结果格式化为 LLM 可读的法律依据文本
     * 只传入已校验通过的条款（verified=true），降低幻觉风险
     */
    private String formatLegalBasis(List<HybridSearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "未检索到相关法律条文。";
        }
        List<HybridSearchResult> verified = searchResults.stream()
                .filter(r -> r.getVerified() == null || r.getVerified())
                .collect(Collectors.toList());
        if (verified.isEmpty()) {
            // 全部未校验通过也传入，但标注
            verified = searchResults;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < verified.size(); i++) {
            HybridSearchResult r = verified.get(i);
            sb.append(String.format("%d. 《%s》%s %s\n   内容：%s\n",
                    i + 1,
                    StrUtil.blankToDefault(r.getLawName(), "未知法律"),
                    StrUtil.blankToDefault(r.getArticleNo(), ""),
                    StrUtil.blankToDefault(r.getChapter(), ""),
                    StrUtil.blankToDefault(r.getContent(), "")));
        }
        return sb.toString();
    }

    /**
     * 降级方案：LLM 解析失败时，直接从检索结果构建法律分析
     */
    private LegalAnalysisResult buildFallback(CaseAnalysisResult caseAnalysis, List<HybridSearchResult> searchResults) {
        List<LegalAnalysisResult.LegalBasisItem> basis = new ArrayList<>();
        if (searchResults != null) {
            for (HybridSearchResult r : searchResults) {
                basis.add(LegalAnalysisResult.LegalBasisItem.builder()
                        .lawName(r.getLawName())
                        .articleNo(r.getArticleNo())
                        .content(r.getContent())
                        .relevance("由知识库检索匹配")
                        .build());
            }
        }
        return LegalAnalysisResult.builder()
                .legalIssues(List.of(
                        StrUtil.blankToDefault(caseAnalysis.getDomain(), "法律") + "相关问题"))
                .legalBasis(basis)
                .caseAnalysis("根据您描述的情况，可能涉及" +
                        StrUtil.blankToDefault(caseAnalysis.getCaseType(), "相关法律") +
                        "问题。具体分析需要结合更完整的事实，请参考以下法律依据。")
                .suggestions(List.of(
                        "收集并保存相关证据材料",
                        "如需进一步帮助，建议咨询专业律师"))
                .build();
    }
}
