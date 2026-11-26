package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 案件列表/详情 VO（CASE-002/CASE-003）
 * 不包含敏感的 description 全文之外的大字段，列表与详情共用
 */
@Data
@Schema(description = "案件信息")
public class CaseVO {

    @Schema(description = "案件ID")
    private Long id;

    @Schema(description = "案件编号")
    private String caseNo;

    @Schema(description = "案件名称")
    private String caseName;

    @Schema(description = "案件类型")
    private String caseType;

    @Schema(description = "案件描述")
    private String description;

    @Schema(description = "案件发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date occurredAt;

    @Schema(description = "案件发生地点")
    private String location;

    @Schema(description = "涉及主体（公司、个人等）")
    private String partiesInvolved;

    @Schema(description = "案件状态(ACTIVE/PENDING/COMPLETED/ARCHIVED)")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @Schema(description = "证据数量")
    private Long evidenceCount;

    @Schema(description = "关联会话数量")
    private Long conversationCount;

    @Schema(description = "案件事实数量（V2.0 问诊上下文展示）")
    private Long factCount;
}
