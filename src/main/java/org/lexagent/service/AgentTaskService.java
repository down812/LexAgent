package org.lexagent.service;

import org.lexagent.model.entity.AgentTask;

/**
 * Agent 任务记录持久化服务
 * <p>
 * 记录多 Agent 协作过程中每个 Agent 的输入/输出/状态/耗时。
 */
public interface AgentTaskService {

    /**
     * 创建任务记录（PENDING 状态）
     *
     * @param conversationId 会话ID（conversations.id 自增主键）
     * @param messageId       消息ID
     * @param agentType       Agent类型
     * @param input           输入内容(JSON)
     * @return 任务实体（含 id）
     */
    AgentTask create(Long conversationId, Long messageId, String agentType, String input);

    /**
     * 创建任务记录（V2.0 阶段五：带案件上下文与任务组标识）
     *
     * @param conversationId 会话ID
     * @param messageId       消息ID
     * @param caseId          所属案件ID（可为 null）
     * @param taskGroupId     一次完整协作任务标识（UUID，可为 null）
     * @param agentType       Agent类型
     * @param input           输入内容(JSON)
     */
    AgentTask create(Long conversationId, Long messageId, Long caseId,
                     String taskGroupId, String agentType, String input);

    /**
     * 标记任务为运行中
     */
    void markRunning(Long taskId);

    /**
     * 标记任务成功
     *
     * @param taskId     任务ID
     * @param output     输出内容(JSON)
     * @param durationMs 耗时(毫秒)
     */
    void markSuccess(Long taskId, String output, Long durationMs);

    /**
     * 标记任务失败
     *
     * @param taskId     任务ID
     * @param errorMsg   错误信息
     * @param durationMs 耗时(毫秒)
     */
    void markFailed(Long taskId, String errorMsg, Long durationMs);
}
