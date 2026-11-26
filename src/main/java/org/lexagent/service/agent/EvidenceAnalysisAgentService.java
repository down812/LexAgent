package org.lexagent.service.agent;

import org.lexagent.model.vo.CaseContextVO;
import org.lexagent.model.vo.EvidenceAnalysisResult;

/**
 * Evidence Analysis Agent（证据分析 Agent，V2.0 阶段五 / AGENT-001）
 * <p>
 * 职责：在问诊链路中分析案件证据材料。
 * 输入：案件上下文（案件信息、事实、OCR 结果概览、已上传证据、证据清单）+ 用户本次问题
 * 输出：证据类型概览、关键信息、证据关联事实、证据缺失情况、证据分析建议
 */
public interface EvidenceAnalysisAgentService {

    /**
     * 综合分析案件证据（面向一次问诊）
     *
     * @param context   案件上下文
     * @param userQuery 用户本次问题
     * @return 证据分析结果
     */
    EvidenceAnalysisResult analyze(CaseContextVO context, String userQuery);
}
