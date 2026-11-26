package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 证据清单项目 VO（LIST-001~004）
 * 含已关联的实际证据信息与系统推荐的候选证据（名称相似度匹配）
 */
@Data
@Schema(description = "证据清单项目")
public class EvidenceChecklistVO {

    @Schema(description = "清单项目ID")
    private Long id;

    @Schema(description = "所属案件ID")
    private Long caseId;

    @Schema(description = "建议证据名称")
    private String evidenceName;

    @Schema(description = "证据用途")
    private String purpose;

    @Schema(description = "状态(REQUIRED/UPLOADED/CONFIRMED/OPTIONAL)")
    private String status;

    @Schema(description = "关联案件事实")
    private String relatedFact;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @Schema(description = "已关联的实际证据")
    private List<RelatedEvidence> relatedEvidences;

    @Schema(description = "系统推荐的候选证据ID（基于名称相似度，供用户确认关联）")
    private List<Long> suggestedEvidenceIds;

    @Data
    @Schema(description = "已关联证据信息")
    public static class RelatedEvidence {
        @Schema(description = "证据ID")
        private Long id;
        @Schema(description = "证据编号")
        private String evidenceNo;
        @Schema(description = "证据名称")
        private String evidenceName;
        @Schema(description = "证据分类")
        private String category;
        @Schema(description = "OCR状态")
        private String ocrStatus;
    }
}
