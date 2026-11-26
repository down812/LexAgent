package org.lexagent.service;

import org.lexagent.model.vo.EvidenceAnalysisVO;
import org.lexagent.model.vo.EvidenceIntegrityVO;

/**
 * 证据智能分析服务（V2.0 阶段三 / Evidence Analysis Agent）
 *
 * 职责：
 * - EVIDENCE-006 证据关键信息提取（关联原文、不确定信息明确标记、不允许补全）
 * - EVIDENCE-007 证据分类推荐（低置信度不自动采纳）
 * - EVIDENCE-008 证据与案件事实关联（给出依据，用户可确认/取消）
 * - EVIDENCE-009 案件证据完整性分析（辅助整理建议）
 */
public interface EvidenceAnalysisService {

    /** 异步触发证据智能分析（EVIDENCE-006/007/008 一次完成） */
    void analyzeAsync(Long evidenceId);

    /** 同步执行分析（供测试/手动触发） */
    EvidenceAnalysisVO analyze(Long evidenceId);

    /** 查询证据最新分析结果（无结果返回 null） */
    EvidenceAnalysisVO getAnalysis(Long evidenceId);

    /**
     * 用户确认/取消证据与案件事实的关联（EVIDENCE-008）
     *
     * @param evidenceId 证据ID
     * @param factId     案件事实ID
     * @param confirmed  true 确认关联 / false 取消关联
     */
    EvidenceAnalysisVO updateRelatedFact(Long evidenceId, Long factId, boolean confirmed);

    /** 案件证据完整性分析（EVIDENCE-009，同步返回） */
    EvidenceIntegrityVO analyzeIntegrity(Long caseId);
}
