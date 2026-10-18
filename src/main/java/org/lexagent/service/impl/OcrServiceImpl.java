package org.lexagent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lexagent.service.OcrService;
import org.lexagent.utils.OcrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * OCR 服务实现
 * 代理 OcrClient 完成 HTTP 调用，统一异常封装
 */
@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    @Autowired
    private OcrClient ocrClient;

    @Override
    public String ocrImage(String absolutePath) {
        log.info("图片 OCR: {}", absolutePath);
        return ocrClient.recognizeImage(absolutePath);
    }

    @Override
    public String ocrPdf(String absolutePath) {
        log.info("扫描 PDF OCR: {}", absolutePath);
        return ocrClient.recognizePdf(absolutePath);
    }

    @Override
    public boolean isAvailable() {
        return ocrClient.isAvailable();
    }
}
