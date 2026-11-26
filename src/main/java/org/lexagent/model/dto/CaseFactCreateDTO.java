package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 案件事实新增 DTO（POST /case/{id}/fact）
 * 用户手动补充案件事实，来源固定为 USER_INPUT 且视为已确认
 */
@Data
@Schema(description = "案件事实新增参数")
public class CaseFactCreateDTO {

    @Schema(description = "事实内容")
    @NotBlank(message = "事实内容不能为空")
    @Size(max = 2000, message = "事实内容不超过2000字")
    private String factContent;
}
