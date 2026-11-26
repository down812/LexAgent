package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评价统计 VO（11.7 管理员评价统计页 / EVAL 统计）
 * 反馈总数、好评率、差评原因分布、回答质量变化趋势
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评价统计结果")
public class FeedbackStatisticsVO {

    @Schema(description = "反馈总数")
    private Long totalCount;

    @Schema(description = "有帮助数")
    private Long helpfulCount;

    @Schema(description = "没帮助数")
    private Long notHelpfulCount;

    @Schema(description = "好评率(0-100整数)")
    private Integer goodRate;

    @Schema(description = "差评原因分布")
    private List<ReasonItem> reasonDistribution;

    @Schema(description = "近14天质量趋势（每日反馈量与好评率）")
    private List<TrendItem> trend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "差评原因分布项")
    public static class ReasonItem {
        @Schema(description = "原因")
        private String reason;
        @Schema(description = "数量")
        private Long count;
        @Schema(description = "占差评比例(0-100)")
        private Integer percent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "每日趋势项")
    public static class TrendItem {
        @Schema(description = "日期（yyyy-MM-dd）")
        private String date;
        @Schema(description = "当日反馈总数")
        private Long total;
        @Schema(description = "当日好评率(0-100，无反馈为 null)")
        private Integer goodRate;
    }
}
