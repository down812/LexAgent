package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证据信息修改 DTO（PUT /evidence/update）
 * 支持修改证据名称、分类、备注、修正识别文本
 */
@Data
@Schema(description = "证据信息修改参数")
public class EvidenceUpdateDTO {

    @Schema(description = "证据ID")
    @NotNull(message = "证据ID不能为空")
    private Long id;

    @Schema(description = "证据名称")
    private String evidenceName;

    @Schema(description = "证据分类")
    private String category;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "用户修正后的识别文本（保留原始识别文本不覆盖）")
    private String correctedText;
}
