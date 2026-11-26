package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证据清单新增项目 DTO（LIST-004：用户手动新增清单项）
 */
@Data
@Schema(description = "证据清单新增项目参数")
public class ChecklistCreateDTO {

    @Schema(description = "所属案件ID")
    @NotNull(message = "案件ID不能为空")
    private Long caseId;

    @Schema(description = "建议证据名称")
    @NotBlank(message = "证据名称不能为空")
    private String evidenceName;

    @Schema(description = "证据用途")
    private String purpose;

    @Schema(description = "状态（默认 REQUIRED，可选 OPTIONAL）")
    private String status;

    @Schema(description = "关联案件事实")
    private String relatedFact;

    @Schema(description = "备注")
    private String remark;
}
