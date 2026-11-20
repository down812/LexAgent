package org.lexagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.mapper.AgentTaskMapper;
import org.lexagent.model.entity.AgentTask;
import org.lexagent.service.AgentTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 任务记录持久化实现
 */
@Slf4j
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Value("${lexagent.rag.agent.persist-tasks:true}")
    private boolean persistTasks;

    @Override
    public AgentTask create(Long conversationId, Long messageId, String agentType, String input) {
        if (!persistTasks) {
            return AgentTask.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .agentType(agentType)
                    .input(input)
                    .status(AgentConstant.TASK_PENDING)
                    .build();
        }
        AgentTask task = AgentTask.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .agentType(agentType)
                .input(input)
                .status(AgentConstant.TASK_PENDING)
                .build();
        agentTaskMapper.insert(task);
        log.debug("AgentTask 已创建：id={}, type={}", task.getId(), agentType);
        return task;
    }

    @Override
    public void markRunning(Long taskId) {
        if (taskId == null || !persistTasks) return;
        agentTaskMapper.update(null,
                new LambdaUpdateWrapper<AgentTask>()
                        .eq(AgentTask::getId, taskId)
                        .set(AgentTask::getStatus, AgentConstant.TASK_RUNNING));
    }

    @Override
    public void markSuccess(Long taskId, String output, Long durationMs) {
        if (taskId == null || !persistTasks) return;
        agentTaskMapper.update(null,
                new LambdaUpdateWrapper<AgentTask>()
                        .eq(AgentTask::getId, taskId)
                        .set(AgentTask::getStatus, AgentConstant.TASK_SUCCESS)
                        .set(AgentTask::getOutput, output)
                        .set(AgentTask::getDurationMs, durationMs));
        log.debug("AgentTask 成功：id={}, duration={}ms", taskId, durationMs);
    }

    @Override
    public void markFailed(Long taskId, String errorMsg, Long durationMs) {
        if (taskId == null || !persistTasks) return;
        agentTaskMapper.update(null,
                new LambdaUpdateWrapper<AgentTask>()
                        .eq(AgentTask::getId, taskId)
                        .set(AgentTask::getStatus, AgentConstant.TASK_FAILED)
                        .set(AgentTask::getErrorMsg, errorMsg)
                        .set(AgentTask::getDurationMs, durationMs));
        log.warn("AgentTask 失败：id={}, error={}", taskId, errorMsg);
    }
}
