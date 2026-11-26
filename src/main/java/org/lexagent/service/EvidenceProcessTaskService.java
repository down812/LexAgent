package org.lexagent.service;

/**
 * 证据 OCR/解析处理任务服务
 *
 * 异步执行：证据上传/重新识别后置 PROCESSING → 文档解析（文本型 PDF/DOCX/TXT 直接解析，
 * 图片与扫描 PDF 走 OCR）→ 保存识别文本 → SUCCESS / FAILED（记录失败原因）
 *
 * 与知识库文件处理共用 ragProcessExecutor 线程池。
 */
public interface EvidenceProcessTaskService {

    /** 异步触发：上传接口/重新识别调用，立即返回不阻塞 */
    void processAsync(Long evidenceId);

    /** 同步执行：实际处理逻辑，供测试/手动调用 */
    void process(Long evidenceId);
}
