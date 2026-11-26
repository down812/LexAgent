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
 * 证据分析结果实体（V2.0 阶段三）
 * 对应 evidence_analysis 表：Evidence Analysis Agent 的结构化分析结果
 * extracted_data / related_facts 为 JSON 字符串（由服务层用 Hutool JSONUtil 组装解析）
 * 一条证据可多次分析，查询时取最新一条
 */
@TableName(value = "evidence_analysis")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceAnalysis implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "证据ID")
    @TableField(value = "evidence_id")
    private Long evidenceId;

    @Schema(description = "证据类型（如：劳动合同/银行转账记录）")
    @TableField(value = "evidence_type")
    private String evidenceType;

    @Schema(description = "提取的结构化信息（JSON：关键信息/不确定信息/分类推荐等）")
    @TableField(value = "extracted_data")
    private String extractedData;

    @Schema(description = "关联案件事实（JSON：含关联依据与用户确认状态）")
    @TableField(value = "related_facts")
    private String relatedFacts;

    @Schema(description = "综合分析结论文本")
    @TableField(value = "analysis_result")
    private String analysisResult;

    @Schema(description = "分析置信度(0-100)")
    @TableField(value = "confidence")
    private BigDecimal confidence;

    @Schema(description = "分析时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
