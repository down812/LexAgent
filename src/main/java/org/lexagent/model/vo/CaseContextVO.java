package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 案件上下文 VO（V2.0 阶段五 / CHAT-001）
 * 问诊链路注入的案件档案：基本信息 + 已确认事实 + 证据概览（含分析摘要）+ 证据清单
 * 由 CaseContextService 组装，供 Supervisor / Evidence Analysis Agent 使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "案件上下文")
public class CaseContextVO {

    @Schema(description = "案件ID")
    private Long caseId;

    @Schema(description = "案件名称")
    private String caseName;

    @Schema(description = "案件编号")
    private String caseNo;

    @Schema(description = "案件类型")
    private String caseType;

    @Schema(description = "案件描述")
    private String description;

    @Schema(description = "已确认的案件事实内容列表")
    private List<String> confirmedFacts;

    @Schema(description = "证据概览列表")
    private List<EvidenceBrief> evidences;

    @Schema(description = "证据清单概要列表（名称 + 状态）")
    private List<String> checklistItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "证据概要")
    public static class EvidenceBrief {
        @Schema(description = "证据名称")
        private String evidenceName;
        @Schema(description = "证据分类")
        private String category;
        @Schema(description = "OCR状态")
        private String ocrStatus;
        @Schema(description = "证据类型（来自智能分析）")
        private String evidenceType;
        @Schema(description = "分析结论摘要")
        private String analysisSummary;
    }

    /**
     * 文本化（供 LLM 提示词注入），控制总长度
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("案件：").append(safe(caseName)).append("（").append(safe(caseNo)).append("，类型：")
                .append(safe(caseType)).append("）\n");
        if (description != null && !description.isEmpty()) {
            sb.append("案件描述：").append(truncate(description, 300)).append("\n");
        }
        sb.append("已确认案件事实：\n");
        if (confirmedFacts == null || confirmedFacts.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (String f : confirmedFacts) {
                sb.append("- ").append(truncate(f, 150)).append("\n");
            }
        }
        sb.append("已上传证据：\n");
        if (evidences == null || evidences.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (EvidenceBrief ev : evidences) {
                sb.append("- ").append(safe(ev.getEvidenceName()))
                        .append("（分类：").append(safe(ev.getCategory()))
                        .append("，识别状态：").append(safe(ev.getOcrStatus()));
                if (ev.getEvidenceType() != null && !ev.getEvidenceType().isEmpty()) {
                    sb.append("，类型：").append(ev.getEvidenceType());
                }
                if (ev.getAnalysisSummary() != null && !ev.getAnalysisSummary().isEmpty()) {
                    sb.append("，分析摘要：").append(truncate(ev.getAnalysisSummary(), 100));
                }
                sb.append("）\n");
            }
        }
        if (checklistItems != null && !checklistItems.isEmpty()) {
            sb.append("证据清单（含待补充项）：").append(String.join("；", checklistItems)).append("\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
