package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Legal Analysis Agent 输出：法律分析结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalAnalysisResult {

    @Schema(description = "涉及的法律问题列表")
    @JsonProperty("legalIssues")
    private List<String> legalIssues;

    @Schema(description = "法律依据列表")
    @JsonProperty("legalBasis")
    private List<LegalBasisItem> legalBasis;

    @Schema(description = "结合案件事实和法律依据的详细分析")
    @JsonProperty("caseAnalysis")
    private String caseAnalysis;

    @Schema(description = "处理建议列表")
    private List<String> suggestions;

    /**
     * 单条法律依据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LegalBasisItem {
        @Schema(description = "法律名称")
        @JsonProperty("lawName")
        private String lawName;

        @Schema(description = "条款编号")
        @JsonProperty("articleNo")
        private String articleNo;

        @Schema(description = "条款内容摘要")
        private String content;

        @Schema(description = "与案件的关联说明")
        private String relevance;
    }
}
