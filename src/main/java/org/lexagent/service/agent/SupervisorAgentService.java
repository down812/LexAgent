package org.lexagent.service.agent;

import org.lexagent.model.vo.AgentEvent;
import org.lexagent.model.vo.FinalLegalAnswer;

import java.util.function.Consumer;

/**
 * Supervisor Agent（编排 Agent）
 * <p>
 * 职责：编排 4 个 Agent 串联执行，生成最终法律回答。
 * 流程：
 *   Case Analysis Agent → Legal Search Agent → Legal Analysis Agent → Reviewer Agent → 最终回答
 * <p>
 * 支持流式回调：每个 Agent 执行前后推送 SSE 事件。
 */
public interface SupervisorAgentService {

    /**
     * 执行多 Agent 协作流程（流式）
     *
     * @param userQuery       用户法律问题描述
     * @param conversationId  会话ID（conversations.id 自增主键）
     * @param messageId         消息ID
     * @param eventCallback   SSE 事件回调（可为 null）
     * @return 最终法律回答
     */
    FinalLegalAnswer execute(String userQuery, Long conversationId, Long messageId,
                             Consumer<AgentEvent> eventCallback);

    /**
     * V2.0 阶段五（CHAT-001/AGENT-002）：带案件上下文的执行
     * 上下文已在 Controller 层完成权限校验与组装（异步线程中无登录态）。
     * 依据请求类型路由：涉及案件证据材料时插入 Evidence Analysis Agent（场景二），
     * 否则走 V1.0 原有流程（场景一）。
     *
     * @param caseContext   案件上下文（null 表示普通咨询，走 V1.0 流程）
     */
    FinalLegalAnswer execute(String userQuery, Long conversationId, Long messageId,
                             org.lexagent.model.vo.CaseContextVO caseContext,
                             Consumer<AgentEvent> eventCallback);
}
