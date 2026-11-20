package org.lexagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * RAG 解析向量化异步任务线程池配置
 *
 * 专为知识库文件"解析 → 结构化切片 → 向量化存储"链路设计，
 * 与 WebMvcConfig 中的通用 asyncTaskExecutor（SSE 等）隔离，互不影响。
 *
 * 任务特征：IO 密集型（PDFBox 读文件 / OCR HTTP 调用 / Embedding API / ES 写入）
 *   - 核心线程 4：避免过多并发解析占用 CPU 与内存（大 PDF 解析较吃内存）
 *   - 最大线程 8：突发流量时扩容
 *   - 队列 20：缓冲排队任务
 *   - 拒绝策略 CallerRunsPolicy：队列满时在调用线程同步执行，保证任务不丢失
 *     （上传接口会短暂阻塞，但比丢任务更安全——法律文档入库不能丢）
 *
 * 与 @Async("ragProcessExecutor") 配合使用。
 */
@Slf4j
@Configuration
public class RagAsyncConfig {

    /**
     * RAG 解析向量化专用线程池
     */
    @Bean("ragProcessExecutor")
    public ThreadPoolTaskExecutor ragProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("rag-process-");
        // 队列满时由调用线程执行，保证任务不丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待正在执行的任务完成（避免解析到一半被中断，文件状态卡在 PROCESSING）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("RAG 解析向量化线程池已初始化: core=4 max=8 queue=20 policy=CallerRuns");
        return executor;
    }
}
