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
 * 回答评价实体（V2.0 12.8）
 * 对应 answer_feedback 表：用户对 AI 回答的评价反馈
 * 每个用户对每条回答仅一条评价记录（唯一键 user_id+message_id），可修改
 */
@TableName(value = "answer_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerFeedback implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "评价用户ID")
    @TableField(value = "user_id")
    private Long userId;

    @Schema(description = "回答消息ID（关联messages.id）")
    @TableField(value = "message_id")
    private Long messageId;

    @Schema(description = "评价结果(1有帮助/0没有帮助)")
    @TableField(value = "rating")
    private Integer rating;

    @Schema(description = "评价原因")
    @TableField(value = "reason")
    private String reason;

    @Schema(description = "文字反馈")
    @TableField(value = "content")
    private String content;

    @Schema(description = "评价时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
