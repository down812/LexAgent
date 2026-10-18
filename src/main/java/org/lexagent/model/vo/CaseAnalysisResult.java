package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Case Analysis Agent 输出：结构化案件信息
 * <p>
 * 将用户自然语言描述转换为结构化案件信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseAnalysisResult {

    @Schema(description = "法律领域（劳动法/消费者权益/民事纠纷等）")
    private String domain;

    @Schema(description = "案件类型（劳动合同解除/商品质量纠纷等）")
    @JsonProperty("caseType")
    private String caseType;

    @Schema(description = "已确认的案件事实列表")
    private List<String> facts;

    @Schema(description = "争议焦点列表")
    @JsonProperty("disputePoints")
    private List<String> disputePoints;

    @Schema(description = "缺失但重要的信息列表")
    @JsonProperty("missingInformation")
    private List<String> missingInformation;
}
