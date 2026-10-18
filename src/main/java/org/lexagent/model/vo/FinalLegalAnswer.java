package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 最终法律回答（面向用户的结构化输出）
 * <p>
 * 对应需求文档第10章的输出结构：
 * 【初步判断】【法律依据】【结合你的情况】【处理建议】【风险提示】
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinalLegalAnswer {

    @Schema(description = "初步判断")
    private String preliminaryJudgment;

    @Schema(description = "法律依据列表")
    private List<LegalAnalysisResult.LegalBasisItem> legalBasis;

    @Schema(description = "结合用户情况的分析")
    private String caseAnalysis;

    @Schema(description = "处理建议列表")
    private List<String> suggestions;

    @Schema(description = "风险提示")
    private String riskWarning;

    @Schema(description = "审核可信度评分(0-100)")
    private Integer confidenceScore;

    @Schema(description = "审核是否通过")
    private Boolean reviewPassed;
}
