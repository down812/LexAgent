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
 * 证据清单实体（V2.0 阶段四）
 * 对应 evidence_checklist 表：根据案件事实生成的个性化证据收集清单
 */
@TableName(value = "evidence_checklist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceChecklist implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "清单项目ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属案件ID")
    @TableField(value = "case_id")
    private Long caseId;

    @Schema(description = "建议证据名称")
    @TableField(value = "evidence_name")
    private String evidenceName;

    @Schema(description = "证据用途")
    @TableField(value = "purpose")
    private String purpose;

    @Schema(description = "状态(REQUIRED/UPLOADED/CONFIRMED/OPTIONAL)")
    @TableField(value = "status")
    private String status;

    @Schema(description = "关联案件事实")
    @TableField(value = "related_fact")
    private String relatedFact;

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
