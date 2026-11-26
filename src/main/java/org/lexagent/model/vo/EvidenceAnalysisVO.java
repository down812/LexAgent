package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 证据智能分析结果 VO（EVIDENCE-006/007/008）
 * Evidence Analysis Agent 输出的结构化分析结果
 */
@Data
@Schema(description = "证据智能分析结果")
public class EvidenceAnalysisVO {

    @Schema(description = "分析记录ID")
    private Long id;

    @Schema(description = "证据ID")
    private Long evidenceId;

    @Schema(description = "证据类型（如：劳动合同、银行转账记录）")
    private String evidenceType;

    @Schema(description = "分类推荐（EVIDENCE-007，低置信度时不自动采纳）")
    private EvidenceCategoryRecommendVO categoryRecommendation;

    @Schema(description = "证据中出现的主体")
    private List<String> parties;

    @Schema(description = "关键信息提取结果（含原文对应内容）")
    private List<EvidenceKeyInfoVO> keyInformation;

    @Schema(description = "无法识别的信息")
    private List<EvidenceUncertainVO> uncertainInformation;

    @Schema(description = "关联的案件事实（含关联依据与确认状态）")
    private List<EvidenceRelatedFactVO> relatedFacts;

    @Schema(description = "综合分析结论")
    private String analysisResult;

    @Schema(description = "分析置信度(0-100)")
    private BigDecimal confidence;

    @Schema(description = "分析时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
