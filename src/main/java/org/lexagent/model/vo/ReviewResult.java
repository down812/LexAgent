package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reviewer Agent 输出：法律分析审核结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResult {

    @Schema(description = "审核是否通过")
    private Boolean passed;

    @Schema(description = "审核发现的问题列表")
    private List<ReviewIssue> issues;

    @Schema(description = "总体审核意见")
    @JsonProperty("overallComment")
    private String overallComment;

    @Schema(description = "可信度评分(0-100)")
    @JsonProperty("confidenceScore")
    private Integer confidenceScore;

    /**
     * 单个审核问题
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewIssue {
        @Schema(description = "问题类型：LEGAL_BASIS/CLAUSE_MATCH/FACT_CONSISTENCY/RISK_EXPRESSION")
        private String type;

        @Schema(description = "严重度：HIGH/MEDIUM/LOW")
        private String severity;

        @Schema(description = "问题描述")
        private String description;

        @Schema(description = "修正建议")
        private String suggestion;
    }
}
