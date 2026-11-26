package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Evidence Analysis Agent 输出（V2.0 阶段五 / AGENT-001）
 * 问诊链路中的案件证据分析结果：
 * 证据类型、关键信息、证据关联事实、证据缺失情况、证据分析建议
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "证据分析 Agent 输出")
public class EvidenceAnalysisResult {

    @Schema(description = "证据总览（整体证据情况说明）")
    private String evidenceOverview;

    @Schema(description = "关键发现列表（从证据中提炼的与本次问题相关的关键信息）")
    private List<String> keyFindings;

    @Schema(description = "证据支撑的案件事实列表")
    private List<String> supportedFacts;

    @Schema(description = "证据缺失情况列表（缺少哪些证据/材料）")
    private List<String> missingEvidence;

    @Schema(description = "证据分析建议列表")
    private List<String> suggestions;

    @Schema(description = "分析置信度(0-100)")
    private Integer confidence;
}
