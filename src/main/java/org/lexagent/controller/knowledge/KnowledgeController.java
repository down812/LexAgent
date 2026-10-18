package org.lexagent.controller.knowledge;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.lexagent.Enum.UserEnum;
import org.lexagent.context.LoginContext;
import org.lexagent.exception.PermissionDeniedException;
import org.lexagent.model.dto.DatasetCreateDTO;
import org.lexagent.model.dto.DatasetFileQueryDTO;
import org.lexagent.model.dto.DatasetQueryDTO;
import org.lexagent.model.dto.DatasetUpdateDTO;
import org.lexagent.model.vo.DatasetFileVO;
import org.lexagent.model.vo.DatasetVO;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalSearchResult;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.DatasetFileService;
import org.lexagent.service.DatasetService;
import org.lexagent.service.HybridSearchService;
import org.lexagent.service.KnowledgeSearchService;
import org.lexagent.utils.OcrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lexagent.constants.RagConstant.NO_PERMISSION_ADMIN_REQUIRED;

/**
 * 法律知识库管理接口
 * 数据集 CRUD + 文件上传与管理 + 知识检索
 */
@Tag(name = "法律知识库管理")
@RestController
@Slf4j
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private DatasetFileService datasetFileService;

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private OcrClient ocrClient;

    // ===================== 数据集管理 =====================

    @PostMapping("/dataset")
    @Operation(summary = "创建知识库", description = "仅管理员可调用")
    public Result<?> createDataset(@Validated @RequestBody DatasetCreateDTO dto) {
        checkAdmin();
        return datasetService.createDataset(dto);
    }

    @PostMapping("/dataset/page")
    @Operation(summary = "分页查询知识库列表")
    public Result<PageResult<DatasetVO>> queryDatasets(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestBody(required = false) DatasetQueryDTO queryDTO) {
        return Result.success(datasetService.queryDatasets(pageNum, pageSize, queryDTO));
    }

    @GetMapping("/dataset/{id}")
    @Operation(summary = "查询知识库详情")
    public Result<DatasetVO> getDataset(@PathVariable Long id) {
        return Result.success(datasetService.getDataset(id));
    }

    @PutMapping("/dataset")
    @Operation(summary = "修改知识库", description = "仅管理员可调用")
    public Result<DatasetVO> updateDataset(@Validated @RequestBody DatasetUpdateDTO dto) {
        checkAdmin();
        return Result.success(datasetService.updateDataset(dto));
    }

    @DeleteMapping("/dataset/{id}")
    @Operation(summary = "删除知识库", description = "仅管理员可调用；存在文件时禁止删除")
    public Result<Long> deleteDataset(@PathVariable Long id) {
        checkAdmin();
        return Result.success(datasetService.deleteDataset(id));
    }

    // ===================== 文件上传与管理 =====================

    @PostMapping("/file/upload")
    @Operation(summary = "上传法律文件", description = "仅管理员可调用；支持 PDF/DOCX/TXT/MD/图片")
    public Result<DatasetFileVO> uploadFile(
            @RequestParam Long datasetId,
            @RequestParam("file") MultipartFile file) {
        checkAdmin();
        return Result.success(datasetFileService.uploadFile(datasetId, file));
    }

    @PostMapping("/file/page")
    @Operation(summary = "分页查询文件列表")
    public Result<PageResult<DatasetFileVO>> queryFiles(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestBody(required = false) DatasetFileQueryDTO queryDTO) {
        return Result.success(datasetFileService.queryFiles(pageNum, pageSize, queryDTO));
    }

    @GetMapping("/file/{id}")
    @Operation(summary = "查询文件处理状态")
    public Result<DatasetFileVO> getFile(@PathVariable Long id) {
        return Result.success(datasetFileService.getFile(id));
    }

    @DeleteMapping("/file/{id}")
    @Operation(summary = "删除文件", description = "仅管理员可调用；同时删除本地文件")
    public Result<Long> deleteFile(@PathVariable Long id) {
        checkAdmin();
        return Result.success(datasetFileService.deleteFile(id));
    }

    // ===================== 知识检索 =====================

    @GetMapping("/ocr/status")
    @Operation(summary = "OCR 服务状态探测", description = "探测 PaddleOCR 微服务是否在线（阶段二）")
    public Result<Map<String, Object>> ocrStatus() {
        boolean enabled = ocrClient.isEnabled();
        boolean available = ocrClient.isAvailable();
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", enabled);
        data.put("available", available);
        data.put("message", available
                ? "OCR 服务在线，可处理扫描件/图片"
                : (enabled ? "OCR 服务未启动，扫描件/图片上传将失败，请先运行 ocr-service"
                          : "OCR 功能已禁用（lexagent.rag.ocr.enabled=false）"));
        return Result.success(data);
    }

    @PostMapping("/search")
    @Operation(summary = "法律知识检索（基础向量检索）", description = "输入问题，返回 Top-K 相关法律条款（供 Legal Search Agent 调用）")
    public Result<List<LegalSearchResult>> search(
            @RequestParam String query,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(defaultValue = "5") Integer topK) {
        return Result.success(knowledgeSearchService.search(query, datasetId, topK));
    }

    @PostMapping("/search/hybrid")
    @Operation(summary = "法律知识混合检索（阶段三·论文创新点）",
            description = "向量+关键词 RRF 融合 → 多特征 Rerank → MySQL 条款校验，防幻觉")
    public Result<List<HybridSearchResult>> hybridSearch(
            @RequestParam String query,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(defaultValue = "5") Integer topK) {
        return Result.success(hybridSearchService.hybridSearch(query, datasetId, topK));
    }

    /**
     * 管理员权限校验：type 为 0(超管) 或 1(管理员) 才允许操作
     */
    private void checkAdmin() {
        Integer type = LoginContext.getUserType();
        if (type == null || type > UserEnum.ADMIN.getCode()) {
            throw new PermissionDeniedException(NO_PERMISSION_ADMIN_REQUIRED);
        }
    }
}
