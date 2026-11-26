package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 案件修改 DTO（CASE-004）
 * 修改案件名称/类型/描述/发生时间/地点/备注
 */
@Data
@Schema(description = "案件修改参数")
public class CaseUpdateDTO {

    @Schema(description = "案件ID")
    @NotNull(message = "案件ID不能为空")
    private Long id;

    @Schema(description = "案件名称")
    private String caseName;

    @Schema(description = "案件类型")
    private String caseType;

    @Schema(description = "案件描述")
    private String description;

    @Schema(description = "案件发生时间")
    private Date occurredAt;

    @Schema(description = "案件发生地点")
    private String location;

    @Schema(description = "涉及主体（公司、个人等）")
    private String partiesInvolved;

    @Schema(description = "备注")
    private String remark;
}
