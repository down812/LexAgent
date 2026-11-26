package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证据分页查询 DTO（EVIDENCE-003）
 */
@Data
@Schema(description = "证据分页查询条件")
public class EvidenceQueryDTO {

    @Schema(description = "所属案件ID")
    private Long caseId;

    @Schema(description = "证据名称（模糊查询）")
    private String evidenceName;

    @Schema(description = "证据分类（精确筛选）")
    private String category;

    @Schema(description = "OCR处理状态（WAITING/PROCESSING/SUCCESS/FAILED）")
    private String ocrStatus;
}
