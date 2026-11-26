package org.lexagent.service.agent;

import org.lexagent.model.vo.CaseAnalysisResult;

/**
 * Case Analysis Agent（案件事实分析 Agent）
 * <p>
 * 职责：将用户自然语言描述转换为结构化案件信息。
 * 输入：用户法律问题
 * 输出：domain / caseType / facts / disputePoints / missingInformation
 */
public interface CaseAnalysisAgentService {

    /**
     * 分析用户问题，提取结构化案件信息
     *
     * @param userQuery 用户法律问题描述
     * @return 结构化案件分析结果
     */
    CaseAnalysisResult analyze(String userQuery);

    /**
     * V2.0 阶段五（CHAT-001）：带案件档案上下文的分析
     * 上下文为空时退化为普通分析
     *
     * @param userQuery       用户本次问题
     * @param caseContextText 案件档案上下文文本（可为 null/空）
     */
    default CaseAnalysisResult analyze(String userQuery, String caseContextText) {
        return analyze(userQuery);
    }
}
