package org.lexagent.service;

/**
 * 文件解析向量化任务服务
 *
 * 把"解析 → 结构化切片 → 向量化存储"链路从 DatasetFileService 拆出，
 * 专为异步执行设计：
 *   - processAsync: @Async 异步执行，上传后立即返回不阻塞
 *   - process:      同步执行，供手动重处理/测试场景调用
 *
 * 拆分原因：Spring @Async 基于 AOP 代理，同类内部调用（uploadFile 调 this.triggerProcess）
 * 不走代理，@Async 不生效。独立 Service 才能被代理拦截。
 */
public interface FileProcessTaskService {

    /**
     * 异步触发文件解析向量化链路（上传后调用，立即返回不等待）
     *
     * @param fileId 文件记录ID
     */
    void processAsync(Long fileId);

    /**
     * 同步执行文件解析向量化链路（手动重处理/测试用）
     *
     * @param fileId 文件记录ID
     */
    void process(Long fileId);
}
