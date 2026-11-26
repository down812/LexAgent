package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 案件事实 VO
 */
@Data
@Schema(description = "案件事实")
public class CaseFactVO {

    @Schema(description = "事实ID")
    private Long id;

    @Schema(description = "案件ID")
    private Long caseId;

    @Schema(description = "事实内容")
    private String factContent;

    @Schema(description = "来源类型(USER_INPUT/OCR_EXTRACTION/AI_ANALYSIS/USER_CORRECTION)")
    private String factSource;

    @Schema(description = "信息置信度(0-100)")
    private java.math.BigDecimal confidence;

    @Schema(description = "是否经用户确认(0否/1是)")
    private Integer confirmed;

    @Schema(description = "创建时间")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
