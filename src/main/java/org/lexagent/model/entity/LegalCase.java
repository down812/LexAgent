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
 * 案件实体（V2.0）
 * 对应 cases 表：用户需要持续管理的实际法律事务
 */
@TableName(value = "cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalCase implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "案件ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "案件编号（CASE+日期+ID）")
    @TableField(value = "case_no")
    private String caseNo;

    @Schema(description = "所属用户ID")
    @TableField(value = "user_id")
    private Long userId;

    @Schema(description = "案件名称")
    @TableField(value = "case_name")
    private String caseName;

    @Schema(description = "案件类型（劳动争议/合同纠纷/消费者权益纠纷等）")
    @TableField(value = "case_type")
    private String caseType;

    @Schema(description = "案件描述")
    @TableField(value = "description")
    private String description;

    @Schema(description = "案件发生时间")
    @TableField(value = "occurred_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date occurredAt;

    @Schema(description = "案件发生地点")
    @TableField(value = "location")
    private String location;

    @Schema(description = "涉及主体（公司、个人等）")
    @TableField(value = "parties_involved")
    private String partiesInvolved;

    @Schema(description = "案件状态(ACTIVE/PENDING/COMPLETED/ARCHIVED)")
    @TableField(value = "status")
    private String status;

    @Schema(description = "备注")
    @TableField(value = "remark")
    private String remark;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
