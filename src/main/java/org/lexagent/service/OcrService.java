package org.lexagent.service;

/**
 * OCR 识别服务
 * 处理图片和扫描版 PDF 的中文文本识别，作为文档解析模块的旁路：
 * - 图片（jpg/png）直接 OCR
 * - 扫描版 PDF（文本层为空）自动转 OCR
 *
 * 实现委托给 OcrClient（HTTP 调用 Python 端 PaddleOCR 微服务）
 */
public interface OcrService {

    /**
     * 识别单张图片
     *
     * @param absolutePath 图片绝对路径
     * @return 识别出的文本（已清洗）
     */
    String ocrImage(String absolutePath);

    /**
     * 识别扫描版 PDF（拆页 + 逐页 OCR）
     *
     * @param absolutePath PDF 绝对路径
     * @return 识别出的文本（已清洗）
     */
    String ocrPdf(String absolutePath);

    /**
     * 判断 OCR 服务是否可用（启动时探测，便于在不可用时降级）
     */
    boolean isAvailable();
}
