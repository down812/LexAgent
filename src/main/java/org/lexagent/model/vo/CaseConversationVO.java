package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 案件关联会话 VO（CASE-006）
 * 在会话信息基础上补充关联时间
 */
@Data
@Schema(description = "案件关联问诊会话")
public class CaseConversationVO {

    @Schema(description = "关联记录ID")
    private Long relationId;

    @Schema(description = "案件ID")
    private Long caseId;

    @Schema(description = "会话主键ID")
    private Long conversationId;

    @Schema(description = "业务会话ID（UUID）")
    private String conversationUuid;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话状态(ACTIVE/ARCHIVED)")
    private String status;

    @Schema(description = "关联时间")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date linkedAt;

    @Schema(description = "会话创建时间")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
