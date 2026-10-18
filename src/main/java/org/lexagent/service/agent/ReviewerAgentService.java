package org.lexagent.service.agent;

import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;
import org.lexagent.model.vo.ReviewResult;

import java.util.List;

/**
 * Reviewer Agent（法律核验 Agent）
 * <p>
 * 职责：对法律分析结果进行可信度审核。
 * 审核内容：
 * 1. 法律依据检查 —— 法律是否真实存在、条款是否有效
 * 2. 条款匹配检查 —— 条款是否支持当前结论
 * 3. 事实一致性检查 —— 是否编造用户未提供的信息
 * 4. 风险表达检查 —— 是否使用绝对化/承诺性表述
 */
public interface ReviewerAgentService {

    /**
     * 审核法律分析结果的可信度
     *
     * @param caseAnalysis  案件分析结果
     * @param legalAnalysis 法律分析结果
     * @param searchResults 检索到的法律条文（作为校验基准）
     * @return 审核结果
     */
    ReviewResult review(CaseAnalysisResult caseAnalysis,
                        LegalAnalysisResult legalAnalysis,
                        List<HybridSearchResult> searchResults);
}
