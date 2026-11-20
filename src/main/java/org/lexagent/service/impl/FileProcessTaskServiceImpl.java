package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.mapper.DatasetFileMapper;
import org.lexagent.mapper.DatasetMapper;
import org.lexagent.model.dto.LegalChunk;
import org.lexagent.model.entity.Dataset;
import org.lexagent.model.entity.DatasetFile;
import org.lexagent.service.DocumentParseService;
import org.lexagent.service.FileProcessTaskService;
import org.lexagent.service.LegalStructureService;
import org.lexagent.service.VectorStoreService;
import org.lexagent.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件解析向量化任务服务实现
 *
 * 异步执行链路：
 *   文档解析 → 法律结构化切片 → 向量化存储（ES + MySQL）
 *   成功置 SUCCESS 并记录 chunkCount，失败置 FAILED 并记录 errorMsg
 *
 * @Async("ragProcessExecutor") 使用 RagAsyncConfig 定义的专用线程池，
 * 与 SSE 等通用异步任务隔离。
 */
@Slf4j
@Service
public class FileProcessTaskServiceImpl implements FileProcessTaskService {

    @Autowired
    private DatasetFileMapper datasetFileMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private DocumentParseService documentParseService;

    @Autowired
    private LegalStructureService legalStructureService;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 异步触发：上传接口调用此方法，立即返回不阻塞
     * 由 RagAsyncConfig.ragProcessExecutor 线程池执行
     */
    @Async("ragProcessExecutor")
    @Override
    public void processAsync(Long fileId) {
        log.info("异步任务开始：fileId={}", fileId);
        try {
            process(fileId);
        } catch (Exception e) {
            // process 内部已捕获并更新状态，这里兜底防止异常逃逸导致线程池异常
            log.error("异步任务异常逃逸：fileId={}", fileId, e);
        }
    }

    /**
     * 同步执行：实际处理逻辑，供手动重处理/测试直接调用
     */
    @Override
    public void process(Long fileId) {
        DatasetFile record = datasetFileMapper.selectById(fileId);
        if (record == null) {
            log.warn("文件记录不存在，跳过处理：fileId={}", fileId);
            return;
        }

        // 置为处理中
        record.setStatus(RagConstant.FILE_STATUS_PROCESSING);
        datasetFileMapper.updateById(record);

        try {
            // 1. 文档解析为纯文本
            String absPath = fileStorageUtil.absolutePath(record.getFilePath());
            String text = documentParseService.parse(absPath, record.getFileType());

            // 2. 获取法律领域（来自所属知识库）
            Dataset dataset = datasetMapper.selectById(record.getDatasetId());
            String domain = (dataset != null) ? dataset.getDomain() : "";

            // 3. 法律文本结构化切片
            List<LegalChunk> chunks = legalStructureService.structure(text, domain);

            // 4. 向量化存储（写入 Elasticsearch + MySQL）
            int count = vectorStoreService.store(chunks, record.getDatasetId(), fileId, domain);

            // 5. 成功
            record.setStatus(RagConstant.FILE_STATUS_SUCCESS);
            record.setChunkCount(count);
            record.setProcessTime(DateTime.now());
            datasetFileMapper.updateById(record);
            log.info("文件[{}]处理完成，切片数={}", fileId, count);
        } catch (Exception e) {
            log.error("文件[{}]处理失败", fileId, e);
            // 重新查询防止 record 被其他线程修改（异步场景下 record 是进入时的快照）
            DatasetFile latest = datasetFileMapper.selectById(fileId);
            if (latest == null) {
                return;
            }
            latest.setStatus(RagConstant.FILE_STATUS_FAILED);
            latest.setErrorMsg(e.getMessage());
            latest.setProcessTime(DateTime.now());
            datasetFileMapper.updateById(latest);
        }
    }
}
