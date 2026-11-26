package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 反馈分页查询 DTO（管理员，14.4 /feedback/page）
 */
@Data
@Schema(description = "反馈分页查询条件")
public class FeedbackQueryDTO {

    @Schema(description = "评价结果筛选（1有帮助/0没帮助）")
    private Integer rating;

    @Schema(description = "差评原因筛选（精确）")
    private String reason;
}
