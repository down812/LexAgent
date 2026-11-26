package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证据分类推荐 VO（EVIDENCE-007）
 * 低置信度时前端提示用户自行确认，不自动采纳
 */
@Data
@Schema(description = "证据分类推荐")
public class EvidenceCategoryRecommendVO {

    @Schema(description = "推荐分类（身份证明/合同协议/财务凭证/沟通记录/书面材料/影像资料/其他材料）")
    private String category;

    @Schema(description = "推荐置信度(0-1)")
    private Double confidence;
}
