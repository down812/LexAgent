package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.mapper.CaseEvidenceMapper;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.service.DocumentParseService;
import org.lexagent.service.EvidenceProcessTaskService;
import org.lexagent.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 证据 OCR/解析处理任务实现（V2.0）
 *
 * 异步执行链路：
 *   置 PROCESSING → 文档解析（复用 DocumentParseService：
 *     - 文本型 PDF → PDFBox 提取；扫描 PDF → 自动转 OCR
 *     - 图片（jpg/png/bmp/webp）→ PaddleOCR
 *     - DOC（97-2003）→ POI scratchpad；DOCX → POI 段落提取；TXT → 直接读取）
 *   → 保存 ocr_text → SUCCESS
 *   失败置 FAILED 并记录失败原因（识别失败不影响案件与证据基本信息，满足可靠性要求）
 */
@Slf4j
@Service
public class EvidenceProcessTaskServiceImpl implements EvidenceProcessTaskService {

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private DocumentParseService documentParseService;

    @Async("ragProcessExecutor")
    @Override
    public void processAsync(Long evidenceId) {
        log.info("证据识别异步任务开始：evidenceId={}", evidenceId);
        try {
            process(evidenceId);
        } catch (Exception e) {
            // process 内部已捕获并更新状态，这里兜底防止异常逃逸
            log.error("证据识别异步任务异常逃逸：evidenceId={}", evidenceId, e);
        }
    }

    @Override
    public void process(Long evidenceId) {
        CaseEvidence record = caseEvidenceMapper.selectById(evidenceId);
        if (record == null) {
            log.warn("证据记录不存在，跳过识别：evidenceId={}", evidenceId);
            return;
        }

        // 防止重复执行：仅 WAITING/FAILED 状态才处理
        if (!RagConstant.FILE_STATUS_WAITING.equals(record.getOcrStatus())
                && !RagConstant.FILE_STATUS_FAILED.equals(record.getOcrStatus())) {
            log.warn("证据[{}]状态为 {}，跳过重复识别", evidenceId, record.getOcrStatus());
            return;
        }

        // 置为识别中
        record.setOcrStatus(RagConstant.FILE_STATUS_PROCESSING);
        caseEvidenceMapper.updateById(record);

        try {
            String absPath = fileStorageUtil.absolutePath(record.getFilePath());
            String text = documentParseService.parse(absPath, record.getFileType());

            record.setOcrText(text);
            record.setOcrStatus(RagConstant.FILE_STATUS_SUCCESS);
            record.setUpdatedAt(DateTime.now());
            caseEvidenceMapper.updateById(record);
            log.info("证据[{}]识别完成，文本长度={}", evidenceId, text.length());
        } catch (Exception e) {
            log.error("证据[{}]识别失败", evidenceId, e);
            CaseEvidence latest = caseEvidenceMapper.selectById(evidenceId);
            if (latest == null) {
                return;
            }
            latest.setOcrStatus(RagConstant.FILE_STATUS_FAILED);
            latest.setOcrErrorMsg(truncate(e.getMessage()));
            latest.setUpdatedAt(DateTime.now());
            caseEvidenceMapper.updateById(latest);
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "未知原因";
        }
        return msg.length() > 400 ? msg.substring(0, 400) : msg;
    }
}
