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
 * 聊天消息实体
 */
@TableName(value = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "会话ID")
    @TableField(value = "conversation_id")
    private Long conversationId;

    @Schema(description = "角色(user/assistant)")
    @TableField(value = "role")
    private String role;

    @Schema(description = "消息内容")
    @TableField(value = "content")
    private String content;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
