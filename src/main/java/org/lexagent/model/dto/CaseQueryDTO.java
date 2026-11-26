package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 案件分页查询 DTO（CASE-002）
 */
@Data
@Schema(description = "案件分页查询条件")
public class CaseQueryDTO {

    @Schema(description = "案件名称（模糊查询）")
    private String caseName;

    @Schema(description = "案件类型（精确筛选）")
    private String caseType;

    @Schema(description = "案件状态（ACTIVE/PENDING/COMPLETED/ARCHIVED）")
    private String status;

    @Schema(description = "排序方向（asc创建时间正序/desc创建时间倒序，默认desc）")
    private String order;
}
