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
 * 案件会话关联实体（V2.0）
 * 对应 case_conversations 表：案件与问诊会话的多对多关联
 * 一个案件可关联多个会话，一个会话原则上关联一个案件
 */
@TableName(value = "case_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseConversation implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "案件ID")
    @TableField(value = "case_id")
    private Long caseId;

    @Schema(description = "会话主键ID（关联conversations.id）")
    @TableField(value = "conversation_id")
    private Long conversationId;

    @Schema(description = "关联时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
