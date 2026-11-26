package org.lexagent.service;

import org.lexagent.model.vo.CaseContextVO;

/**
 * 案件上下文组装服务（V2.0 阶段五 / CHAT-001）
 * 为问诊链路加载案件档案：案件信息 + 已确认事实 + 证据概览（含分析摘要）+ 证据清单
 */
public interface CaseContextService {

    /**
     * 加载案件上下文（含属主/管理员权限校验）
     *
     * @param caseId 案件ID
     * @return 案件上下文
     */
    CaseContextVO load(Long caseId);
}
