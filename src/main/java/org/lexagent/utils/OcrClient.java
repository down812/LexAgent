package org.lexagent.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * OCR 微服务 HTTP 客户端
 * 调用 Python 端 FastAPI + PaddleOCR 服务，识别图片/扫描 PDF 中的中文文本
 *
 * 架构（阶段二改进版）：
 *   - 图片：直接调 /ocr/recognize（单次请求 3-5 秒）
 *   - PDF：Java 端用 PDFBox 拆页转图片 → 逐页调 /ocr/recognize → 合并文本
 *     好处：每次 HTTP 请求只 OCR 一页（3-5秒），永不超时；可实时输出进度；
 *           单页失败可跳过；不传整个 PDF 给 Python，减少网络传输。
 *
 * 依赖 Hutool HttpUtil（项目已引入 hutool-all）+ PDFBox 3.0.3（阶段一已引入）
 */
@Slf4j
@Component
public class OcrClient {

    @Value("${lexagent.rag.ocr.base-url:http://localhost:8501}")
    private String baseUrl;

    @Value("${lexagent.rag.ocr.enabled:true}")
    private boolean enabled;

    @Value("${lexagent.rag.ocr.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /**
     * HTTP 读取超时（毫秒）—— 单页 OCR 3-5 秒，留 60 秒兜底足够
     * 注意：改造后单次请求只 OCR 一页，不再需要 5 分钟超时
     */
    @Value("${lexagent.rag.ocr.read-timeout-ms:60000}")
    private int readTimeoutMs;

    /**
     * PDF 转图片 DPI，越大越清晰越慢（100-400，默认 200）
     */
    @Value("${lexagent.rag.ocr.pdf-dpi:200}")
    private int pdfDpi;

    /**
     * 识别单张图片
     *
     * @param imagePath 图片绝对路径
     * @return 识别出的文本（已清洗）
     */
    public String recognizeImage(String imagePath) {
        return doRecognizeImage(imagePath);
    }

    /**
     * 识别扫描版 PDF（Java 端 PDFBox 拆页 → 逐页调单图 OCR → 合并文本）
     *
     * @param pdfPath PDF 绝对路径
     * @return 识别出的全文（已清洗，页间以空行分隔）
     */
    public String recognizePdf(String pdfPath) {
        if (!enabled) {
            throw new RuntimeException("OCR 服务未启用（lexagent.rag.ocr.enabled=false）");
        }
        File pdfFile = FileUtil.file(pdfPath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new RuntimeException("OCR 文件不存在: " + pdfPath);
        }

        log.info("扫描 PDF OCR（Java 拆页模式）: {} 大小={}KB DPI={}", pdfFile.getName(), pdfFile.length() / 1024, pdfDpi);
        long start = System.currentTimeMillis();

        StringBuilder fullText = new StringBuilder();
        File tempImgDir = null;
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            int pageCount = doc.getNumberOfPages();
            log.info("PDF 共 {} 页，开始逐页 OCR...", pageCount);

            PDFRenderer renderer = new PDFRenderer(doc);
            tempImgDir = Files.createTempDirectory("lexagent-ocr-").toFile();

            int successCount = 0;
            for (int i = 0; i < pageCount; i++) {
                long pageStart = System.currentTimeMillis();
                File tempImg = new File(tempImgDir, "page-" + (i + 1) + ".png");
                try {
                    // 渲染当前页为图片
                    BufferedImage img = renderer.renderImageWithDPI(i, pdfDpi);
                    ImageIO.write(img, "png", tempImg);

                    // 调单图 OCR
                    String pageText = doRecognizeImage(tempImg.getAbsolutePath());
                    if (StrUtil.isNotBlank(pageText)) {
                        if (fullText.length() > 0) {
                            fullText.append("\n\n");
                        }
                        fullText.append(pageText);
                        successCount++;
                    }
                    long pageCost = System.currentTimeMillis() - pageStart;
                    log.info("第 {}/{} 页 OCR 完成，耗时={}ms", i + 1, pageCount, pageCost);
                } catch (Exception e) {
                    // 单页失败不影响其他页，记录警告继续
                    log.warn("第 {}/{} 页 OCR 失败，跳过: {}", i + 1, pageCount, e.getMessage());
                } finally {
                    // 及时清理临时图片，避免磁盘占用
                    FileUtil.del(tempImg);
                }
            }

            long totalCost = System.currentTimeMillis() - start;
            log.info("PDF OCR 全部完成: 总页数={} 成功页={} 文本长度={} 总耗时={}ms",
                    pageCount, successCount, fullText.length(), totalCost);

            if (fullText.length() == 0) {
                throw new RuntimeException("PDF 所有页 OCR 均失败或无文本，请检查 PDF 是否为空白扫描件");
            }
            return fullText.toString();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 拆页 OCR 异常: {}", e.getMessage(), e);
            throw new RuntimeException("PDF OCR 失败: " + e.getMessage(), e);
        } finally {
            // 清理临时目录
            if (tempImgDir != null) {
                FileUtil.del(tempImgDir);
            }
        }
    }

    /**
     * 健康检查（不抛异常，返回布尔）
     */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            HttpResponse resp = HttpRequest.get(baseUrl + "/health")
                    .timeout(connectTimeoutMs)
                    .execute();
            return resp.isOk() && JSONUtil.parseObj(resp.body()).getStr("status", "").equals("ok");
        } catch (Exception e) {
            log.warn("OCR 服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 调用 OCR 服务识别单张图片
     */
    private String doRecognizeImage(String imagePath) {
        if (!enabled) {
            throw new RuntimeException("OCR 服务未启用（lexagent.rag.ocr.enabled=false）");
        }
        File file = FileUtil.file(imagePath);
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("OCR 图片不存在: " + imagePath);
        }

        String url = baseUrl + "/ocr/recognize";
        Map<String, Object> form = new HashMap<>();
        form.put("file", file);

        log.info("调用 OCR 单图识别: {} 大小={}KB", file.getName(), file.length() / 1024);
        try {
            HttpResponse resp = HttpRequest.post(url)
                    .form(form)
                    .setConnectionTimeout(connectTimeoutMs)
                    .setReadTimeout(readTimeoutMs)
                    .execute();
            if (!resp.isOk()) {
                throw new RuntimeException("OCR 服务返回错误: HTTP " + resp.getStatus() + " " + resp.body());
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            String text = json.getStr("text", "");
            int length = json.getInt("length", 0);
            log.info("OCR 单图完成: 文本长度={}", length);
            return text;
        } catch (Exception e) {
            log.error("OCR 单图调用失败: {}", e.getMessage());
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }
}
