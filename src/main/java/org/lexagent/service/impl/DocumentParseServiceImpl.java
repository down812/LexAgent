package org.lexagent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.lexagent.constants.RagConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.service.DocumentParseService;
import org.lexagent.service.OcrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析服务实现
 * - PDF 文本型：Apache PDFBox 3.x（Loader.loadPDF + PDFTextStripper）
 * - PDF 扫描型：文本层为空时自动转 OCR（阶段二，调 Python PaddleOCR 微服务）
 * - DOCX：Apache POI（XWPFDocument 段落提取）
 * - TXT / Markdown：直接 UTF-8 读取
 * - 图片（jpg/png）：阶段二 OCR 识别
 */
@Slf4j
@Service
public class DocumentParseServiceImpl implements DocumentParseService {

    @Autowired
    private OcrService ocrService;

    @Override
    public String parse(String absolutePath, String fileType) throws Exception {
        switch (fileType) {
            case RagConstant.FILE_TYPE_PDF:
                return parsePdf(absolutePath);
            case RagConstant.FILE_TYPE_DOCX:
                return parseDocx(absolutePath);
            case RagConstant.FILE_TYPE_TXT:
            case RagConstant.FILE_TYPE_MD:
                return parseText(absolutePath);
            case RagConstant.FILE_TYPE_IMAGE:
                return parseImage(absolutePath);
            default:
                throw new BusinessException(RagConstant.FILE_TYPE_NOT_SUPPORT);
        }
    }

    /**
     * PDF 解析：
     * 1. 先用 PDFBox 提取文本层
     * 2. 若每页平均字符数过低（判定为扫描件）→ 自动转 OCR
     */
    private String parsePdf(String path) throws Exception {
        int pageCount;
        String text;
        try (PDDocument doc = Loader.loadPDF(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
            pageCount = doc.getNumberOfPages();
            log.info("PDF 文本层提取完成：{} 页，文本长度 {}", pageCount, text.length());
        }

        // 扫描件判定：平均每页字符数过低
        double avgCharsPerPage = (pageCount == 0) ? 0 : (double) text.length() / pageCount;
        if (avgCharsPerPage < RagConstant.SCANNED_PDF_MIN_CHARS_PER_PAGE) {
            log.info("PDF 疑似扫描件（平均 {} 字符/页），转 OCR 识别", String.format("%.1f", avgCharsPerPage));
            if (!ocrService.isAvailable()) {
                throw new BusinessException(RagConstant.OCR_SERVICE_UNAVAILABLE);
            }
            String ocrText = ocrService.ocrPdf(path);
            // OCR 文本已被 Python 端清洗过，这里再过一遍 clean 保证一致性
            return clean(ocrText);
        }
        return clean(text);
    }

    /**
     * Word DOCX 解析：按段落提取
     */
    private String parseDocx(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(path);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isEmpty()) {
                    sb.append(t).append("\n");
                }
            }
            String text = sb.toString();
            log.info("DOCX 解析完成：文本长度 {}", text.length());
            return clean(text);
        }
    }

    /**
     * TXT / Markdown 解析：直接 UTF-8 读取
     */
    private String parseText(String path) throws Exception {
        String text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        log.info("{} 解析完成：文本长度 {}", path, text.length());
        return clean(text);
    }

    /**
     * 图片解析（阶段二）：调用 OCR 服务识别中文文本
     */
    private String parseImage(String path) throws Exception {
        if (!ocrService.isAvailable()) {
            throw new BusinessException(RagConstant.OCR_SERVICE_UNAVAILABLE);
        }
        log.info("图片 OCR 解析: {}", path);
        String text = ocrService.ocrImage(path);
        return clean(text);
    }

    /**
     * 文本清洗：
     * - 统一换行符
     * - 去除不间断空格、零宽字符、BOM
     * - 压缩连续空行
     */
    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\u00A0\u200B\uFEFF]", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
