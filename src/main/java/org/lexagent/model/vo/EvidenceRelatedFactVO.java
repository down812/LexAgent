package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证据关联案件事实 VO（EVIDENCE-008）
 * 关联结果必须给出依据，用户可确认或取消
 */
@Data
@Schema(description = "证据关联案件事实项")
public class EvidenceRelatedFactVO {

    @Schema(description = "案件事实ID")
    private Long factId;

    @Schema(description = "案件事实内容（分析时快照，避免事实删除后展示异常）")
    private String factContent;

    @Schema(description = "关联依据")
    private String reason;

    @Schema(description = "用户是否已确认关联")
    private Boolean confirmed;
}
