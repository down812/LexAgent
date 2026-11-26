package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 * 案件创建 DTO（CASE-001）
 */
@Data
@Schema(description = "案件创建参数")
public class CaseCreateDTO {

    @Schema(description = "案件名称")
    @NotBlank(message = "案件名称不能为空")
    private String caseName;

    @Schema(description = "案件类型（劳动争议/合同纠纷/消费者权益纠纷等）")
    @NotBlank(message = "案件类型不能为空")
    private String caseType;

    @Schema(description = "案件描述")
    @NotBlank(message = "案件描述不能为空")
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
