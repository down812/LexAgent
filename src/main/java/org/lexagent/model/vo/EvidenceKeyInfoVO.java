package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证据关键信息项 VO（EVIDENCE-006）
 * 每项提取结果关联原文，防幻觉
 */
@Data
@Schema(description = "证据关键信息项")
public class EvidenceKeyInfoVO {

    @Schema(description = "字段名（如：合同期限、工资标准）")
    private String field;

    @Schema(description = "从原文提取的字段值")
    private String value;

    @Schema(description = "原文对应内容")
    private String sourceText;
}
