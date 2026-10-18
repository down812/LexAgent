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
}
