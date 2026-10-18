package org.lexagent.service.agent;

import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;

import java.util.List;

/**
 * Legal Analysis Agent（法律分析 Agent）
 * <p>
 * 职责：结合案件事实 + 法律依据 + 检索结果，生成法律分析。
 * 输出：涉及法律问题 / 法律依据 / 案件分析 / 处理建议
 * <p>
 * 约束：基于知识库内容，不编造法律条款，对不确定信息进行说明。
 */
public interface LegalAnalysisAgentService {

    /**
     * 结合案件信息和法律依据生成法律分析
     *
     * @param caseAnalysis 案件分析结果
     * @param searchResults 检索到的法律条文
     * @return 法律分析结果
     */
    LegalAnalysisResult analyze(CaseAnalysisResult caseAnalysis, List<HybridSearchResult> searchResults);
}
