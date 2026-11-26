package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证据不确定信息项 VO（EVIDENCE-006）
 * 无法识别的信息明确标记，不允许补全
 */
@Data
@Schema(description = "证据不确定信息项")
public class EvidenceUncertainVO {

    @Schema(description = "无法识别的字段名")
    private String field;

    @Schema(description = "无法识别的原因")
    private String reason;
}
