package org.lexagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 执行记录实体
 * 记录多 Agent 协作过程中每个 Agent 的输入/输出/状态
 */
@TableName(value = "agent_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联会话ID")
    @TableField(value = "conversation_id")
    private Long conversationId;

    @Schema(description = "关联消息ID")
    @TableField(value = "message_id")
    private Long messageId;

    @Schema(description = "Agent类型(CASE_ANALYSIS/LEGAL_SEARCH/LEGAL_ANALYSIS/REVIEWER)")
    @TableField(value = "agent_type")
    private String agentType;

    @Schema(description = "输入内容(JSON)")
    @TableField(value = "input")
    private String input;

    @Schema(description = "输出内容(JSON)")
    @TableField(value = "output")
    private String output;

    @Schema(description = "状态(PENDING/RUNNING/SUCCESS/FAILED)")
    @TableField(value = "status")
    private String status;

    @Schema(description = "执行耗时(毫秒)")
    @TableField(value = "duration_ms")
    private Long durationMs;

    @Schema(description = "失败原因")
    @TableField(value = "error_msg")
    private String errorMsg;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
