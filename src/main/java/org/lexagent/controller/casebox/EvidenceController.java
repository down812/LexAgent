package org.lexagent.controller.casebox;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.lexagent.model.dto.EvidenceQueryDTO;
import org.lexagent.model.dto.EvidenceUpdateDTO;
import org.lexagent.model.vo.EvidenceAnalysisVO;
import org.lexagent.model.vo.EvidenceIntegrityVO;
import org.lexagent.model.vo.EvidenceVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.CaseEvidenceService;
import org.lexagent.service.EvidenceAnalysisService;

/**
 * 案件证据管理接口（V2.0 第四章）
 * 证据上传/分页/详情/修改/删除/重新 OCR/获取 OCR 文本
 * 所有接口均需登录，数据权限：普通用户仅能访问自己的证据
 */
@Tag(name = "案件证据管理")
@RestController
@Slf4j
@RequestMapping("/evidence")
public class EvidenceController {

    private final CaseEvidenceService caseEvidenceService;

    private final EvidenceAnalysisService evidenceAnalysisService;

    public EvidenceController(CaseEvidenceService caseEvidenceService,
                              EvidenceAnalysisService evidenceAnalysisService) {
        this.caseEvidenceService = caseEvidenceService;
        this.evidenceAnalysisService = evidenceAnalysisService;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传证据（EVIDENCE-001）",
            description = "支持 JPG/PNG/BMP/WEBP 图片、PDF、DOC、DOCX、TXT；上传后自动异步触发 OCR/解析")
    public Result<EvidenceVO> uploadEvidence(
            @RequestParam Long caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String evidenceName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String remark) {
        return Result.success(caseEvidenceService.uploadEvidence(caseId, file, evidenceName, category, remark));
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询证据（EVIDENCE-003）",
            description = "支持按案件/名称/分类/OCR状态筛选；普通用户仅返回自己的证据")
    public Result<PageResult<EvidenceVO>> queryEvidences(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestBody(required = false) EvidenceQueryDTO queryDTO) {
        return Result.success(caseEvidenceService.queryEvidences(pageNum, pageSize, queryDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询证据详情（EVIDENCE-005）", description = "含 OCR 识别文本与用户修正文本")
    public Result<EvidenceVO> getEvidence(@PathVariable Long id) {
        return Result.success(caseEvidenceService.getEvidence(id));
    }

    @PutMapping("/update")
    @Operation(summary = "修改证据信息", description = "修改名称/分类/备注/修正识别文本；修正文本单独保存不覆盖原始识别结果")
    public Result<EvidenceVO> updateEvidence(@Validated @RequestBody EvidenceUpdateDTO dto) {
        return Result.success(caseEvidenceService.updateEvidence(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除证据", description = "同时删除本地证据文件")
    public Result<Long> deleteEvidence(@PathVariable Long id) {
        return Result.success(caseEvidenceService.deleteEvidence(id));
    }

    @PostMapping("/{id}/ocr")
    @Operation(summary = "重新 OCR 识别（EVIDENCE-004）", description = "重置识别状态并异步重新执行识别")
    public Result<EvidenceVO> retryOcr(@PathVariable Long id) {
        return Result.success(caseEvidenceService.retryOcr(id));
    }

    @GetMapping("/{id}/ocr")
    @Operation(summary = "获取 OCR 识别文本", description = "返回证据的原始识别文本")
    public Result<String> getOcrText(@PathVariable Long id) {
        return Result.success(caseEvidenceService.getOcrText(id));
    }

    // ===================== 智能证据分析（V2.0 阶段三） =====================

    @PostMapping("/{id}/analyze")
    @Operation(summary = "触发证据智能分析（EVIDENCE-006/007/008）",
            description = "异步执行：证据类型识别 + 关键信息提取 + 分类推荐 + 案件事实关联；需先完成 OCR 识别")
    public Result<Void> analyzeEvidence(@PathVariable Long id) {
        // 先做权限与状态校验（不通过会抛业务异常）
        caseEvidenceService.checkEvidencePermission(id);
        evidenceAnalysisService.analyzeAsync(id);
        return Result.success();
    }

    @GetMapping("/{id}/analysis")
    @Operation(summary = "查询证据分析结果",
            description = "返回最新一次结构化分析结果（关键信息/分类推荐/关联事实）；无结果返回 data=null")
    public Result<EvidenceAnalysisVO> getAnalysis(@PathVariable Long id) {
        caseEvidenceService.checkEvidencePermission(id);
        return Result.success(evidenceAnalysisService.getAnalysis(id));
    }

    @PutMapping("/{id}/analysis/relation")
    @Operation(summary = "确认/取消证据与案件事实关联（EVIDENCE-008）",
            description = "confirmed=true 确认关联，false 取消关联")
    public Result<EvidenceAnalysisVO> updateRelation(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody @Validated RelationUpdateDTO dto) {
        caseEvidenceService.checkEvidencePermission(id);
        return Result.success(evidenceAnalysisService.updateRelatedFact(id, dto.getFactId(), dto.getConfirmed()));
    }

    @PostMapping("/case/{caseId}/integrity")
    @Operation(summary = "案件证据完整性分析（EVIDENCE-009）",
            description = "同步返回：已覆盖事实/未覆盖事实/建议补充材料/待核实信息；仅辅助整理建议")
    public Result<EvidenceIntegrityVO> analyzeIntegrity(@PathVariable Long caseId) {
        return Result.success(evidenceAnalysisService.analyzeIntegrity(caseId));
    }

    /**
     * 关联确认请求体
     */
    @Data
    public static class RelationUpdateDTO {
        @NotNull(message = "案件事实ID不能为空")
        private Long factId;
        @NotNull(message = "确认状态不能为空")
        private Boolean confirmed;

        public Long getFactId() {
            return factId;
        }

        public void setFactId(Long factId) {
            this.factId = factId;
        }

        public Boolean getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(Boolean confirmed) {
            this.confirmed = confirmed;
        }
    }
}
