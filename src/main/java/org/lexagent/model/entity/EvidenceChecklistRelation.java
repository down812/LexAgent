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
 * 证据清单关联实体（V2.0 阶段四）
 * 对应 evidence_checklist_relation 表：清单项目与实际上传证据的关联
 */
@TableName(value = "evidence_checklist_relation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceChecklistRelation implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "清单项目ID")
    @TableField(value = "checklist_id")
    private Long checklistId;

    @Schema(description = "证据ID")
    @TableField(value = "evidence_id")
    private Long evidenceId;

    @Schema(description = "关联时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
