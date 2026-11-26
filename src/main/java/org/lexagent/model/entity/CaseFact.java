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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 案件事实实体（V2.0）
 * 对应 case_facts 表：案件的事实记录
 * 注意：AI 提取事实（AI_ANALYSIS/OCR_EXTRACTION）不能默认视为用户确认事实
 */
@TableName(value = "case_facts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseFact implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "案件ID")
    @TableField(value = "case_id")
    private Long caseId;

    @Schema(description = "事实内容")
    @TableField(value = "fact_content")
    private String factContent;

    @Schema(description = "来源类型(USER_INPUT/OCR_EXTRACTION/AI_ANALYSIS/USER_CORRECTION)")
    @TableField(value = "fact_source")
    private String factSource;

    @Schema(description = "信息置信度(0-100)")
    @TableField(value = "confidence")
    private BigDecimal confidence;

    @Schema(description = "是否经用户确认(0否/1是)")
    @TableField(value = "confirmed")
    private Integer confirmed;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
