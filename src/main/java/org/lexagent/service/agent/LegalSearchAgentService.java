package org.lexagent.service.agent;

import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;

import java.util.List;

/**
 * Legal Search Agent（法律检索 Agent）
 * <p>
 * 职责：根据案件分析结果检索相关法律法规。
 * 流程：案件信息 → 生成查询条件 → 混合检索 → 返回相关法律条文
 * <p>
 * 本 Agent 融合 LLM 生成检索 query + HybridSearchService 向量+关键词混合检索。
 */
public interface LegalSearchAgentService {

    /**
     * 根据案件分析结果检索相关法律条文
     *
     * @param caseAnalysis 案件分析结果
     * @param topK        返回条数
     * @return 检索到的法律条文列表
     */
    List<HybridSearchResult> search(CaseAnalysisResult caseAnalysis, int topK);
}
