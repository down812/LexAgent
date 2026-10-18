package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.PromptConstant;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.service.HybridSearchService;
import org.lexagent.service.KnowledgeSearchService;
import org.lexagent.service.LlmService;
import org.lexagent.service.agent.LegalSearchAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Legal Search Agent 实现
 * <p>
 * 流程：
 * 1. 调用 LLM 从案件分析结果生成检索查询（覆盖法律领域、争议焦点）
 * 2. 对每个查询调用 HybridSearchService 混合检索
 * 3. 合并去重，按 rerankScore 降序返回 Top-K
 * <p>
 * 当 LLM 不可用时降级：直接用案件事实拼接检索。
 */
@Slf4j
@Service
public class LegalSearchAgentServiceImpl implements LegalSearchAgentService {

    @Autowired
    private LlmService llmService;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @Override
    public List<HybridSearchResult> search(CaseAnalysisResult caseAnalysis, int topK) {
        if (caseAnalysis == null) {
            return new ArrayList<>();
        }
        if (topK <= 0) {
            topK = 5;
        }

        log.info("[LegalSearch] 开始检索，domain={}, caseType={}", caseAnalysis.getDomain(), caseAnalysis.getCaseType());
        long start = System.currentTimeMillis();

        // 1. 生成检索查询
        List<String> queries = generateQueries(caseAnalysis);
        log.info("[LegalSearch] 生成 {} 个检索查询", queries.size());

        // 2. 多查询检索 + 合并去重
        Set<HybridSearchResult> merged = new LinkedHashSet<>();
        for (String q : queries) {
            try {
                List<HybridSearchResult> hits = hybridSearchService.hybridSearch(q, null, topK);
                merged.addAll(hits);
            } catch (Exception e) {
                log.warn("[LegalSearch] 混合检索失败，降级为基础向量检索：{}", e.getMessage());
                // 降级：基础向量检索
                var baseHits = knowledgeSearchService.search(q, null, topK);
                for (var b : baseHits) {
                    merged.add(HybridSearchResult.builder()
                            .lawName(b.getLawName())
                            .articleNo(b.getArticleNo())
                            .chapter(b.getChapter())
                            .content(b.getContent())
                            .domain(b.getDomain())
                            .vectorScore(b.getScore())
                            .verified(true)
                            .matchType("VECTOR")
                            .build());
                }
            }
        }

        // 3. 按分数降序取 Top-K
        List<HybridSearchResult> results = new ArrayList<>(merged);
        results.sort((a, b) -> {
            double sa = a.getRerankScore() != null ? a.getRerankScore()
                    : (a.getVectorScore() != null ? a.getVectorScore() : 0);
            double sb = b.getRerankScore() != null ? b.getRerankScore()
                    : (b.getVectorScore() != null ? b.getVectorScore() : 0);
            return Double.compare(sb, sa);
        });
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[LegalSearch] 检索完成：召回 {} 条，耗时={}ms", results.size(), cost);
        return results;
    }

    /**
     * 调用 LLM 生成检索查询；LLM 不可用时降级为基于案件事实的查询拼接
     */
    private List<String> generateQueries(CaseAnalysisResult caseAnalysis) {
        List<String> queries = new ArrayList<>();

        if (llmService.isAvailable()) {
            try {
                String caseJson = JSONUtil.toJsonStr(caseAnalysis);
                String userPrompt = String.format(PromptConstant.LEGAL_SEARCH_USER_TEMPLATE, caseJson);
                String llmResponse = llmService.chat(PromptConstant.LEGAL_SEARCH_SYSTEM, userPrompt);
                String json = llmService.extractJson(llmResponse);

                JSONObject obj = JSONUtil.parseObj(json);
                JSONArray arr = obj.getJSONArray("queries");
                if (arr != null) {
                    for (Object o : arr) {
                        if (o != null && StrUtil.isNotBlank(o.toString())) {
                            queries.add(o.toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[LegalSearch] LLM 生成查询失败，降级为拼接查询：{}", e.getMessage());
            }
        }

        // 降级或补充：基于案件事实拼接查询
        if (queries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (StrUtil.isNotBlank(caseAnalysis.getDomain())) {
                sb.append(caseAnalysis.getDomain()).append(" ");
            }
            if (StrUtil.isNotBlank(caseAnalysis.getCaseType())) {
                sb.append(caseAnalysis.getCaseType()).append(" ");
            }
            if (caseAnalysis.getDisputePoints() != null) {
                for (String dp : caseAnalysis.getDisputePoints()) {
                    sb.append(dp).append(" ");
                }
            }
            if (caseAnalysis.getFacts() != null) {
                for (String f : caseAnalysis.getFacts()) {
                    sb.append(f).append(" ");
                }
            }
            queries.add(sb.toString().trim());
        }

        return queries;
    }
}
