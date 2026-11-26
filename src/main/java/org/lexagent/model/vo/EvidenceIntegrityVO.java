package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 案件证据完整性分析 VO（EVIDENCE-009）
 * 系统只提供辅助整理建议，不宣称证据必然被法院采纳
 */
@Data
@Schema(description = "案件证据完整性分析结果")
public class EvidenceIntegrityVO {

    @Schema(description = "已有证据覆盖的案件事实")
    private List<CoveredFact> coveredFacts;

    @Schema(description = "尚未覆盖的案件事实")
    private List<String> uncoveredFacts;

    @Schema(description = "建议补充的材料")
    private List<SuggestedMaterial> suggestedMaterials;

    @Schema(description = "需要进一步核实的信息")
    private List<String> toVerify;

    @Schema(description = "整体证据收集情况说明")
    private String summary;

    @Data
    @Schema(description = "已覆盖事实项")
    public static class CoveredFact {
        @Schema(description = "案件事实")
        private String fact;
        @Schema(description = "覆盖该事实的证据名称")
        private List<String> evidences;
    }

    @Data
    @Schema(description = "建议补充材料项")
    public static class SuggestedMaterial {
        @Schema(description = "材料名称")
        private String material;
        @Schema(description = "材料用途")
        private String purpose;
        @Schema(description = "优先级(HIGH/MEDIUM/LOW)")
        private String priority;
    }
}
