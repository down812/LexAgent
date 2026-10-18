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
     * @param conversationId  会话ID
     * @param messageId         消息ID
     * @param eventCallback   SSE 事件回调（可为 null）
     * @return 最终法律回答
     */
    FinalLegalAnswer execute(String userQuery, String conversationId, Long messageId,
                             Consumer<AgentEvent> eventCallback);
}
