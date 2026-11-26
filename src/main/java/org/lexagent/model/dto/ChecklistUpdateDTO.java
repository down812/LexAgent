package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证据清单修改 DTO（LIST-004）
 * 支持修改名称/用途/状态/关联事实/备注，留空不修改
 */
@Data
@Schema(description = "证据清单修改参数")
public class ChecklistUpdateDTO {

    @Schema(description = "清单项目ID")
    @NotNull(message = "清单项目ID不能为空")
    private Long id;

    @Schema(description = "建议证据名称")
    private String evidenceName;

    @Schema(description = "证据用途")
    private String purpose;

    @Schema(description = "状态(REQUIRED/UPLOADED/CONFIRMED/OPTIONAL)")
    private String status;

    @Schema(description = "关联案件事实")
    private String relatedFact;

    @Schema(description = "备注")
    private String remark;
}
