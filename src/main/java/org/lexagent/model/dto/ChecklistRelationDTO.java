package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证据清单关联实际证据 DTO（LIST-002）
 */
@Data
@Schema(description = "证据清单关联实际证据参数")
public class ChecklistRelationDTO {

    @Schema(description = "清单项目ID")
    @NotNull(message = "清单项目ID不能为空")
    private Long checklistId;

    @Schema(description = "证据ID")
    @NotNull(message = "证据ID不能为空")
    private Long evidenceId;
}
