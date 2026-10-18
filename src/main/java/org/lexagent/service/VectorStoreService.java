package org.lexagent.service;

import org.lexagent.model.dto.LegalChunk;

import java.util.List;

/**
 * 向量化存储服务
 * 将法律条款切片向量化并写入 Elasticsearch，同时同步结构化条款到 MySQL 供核验
 */
public interface VectorStoreService {

    /**
     * 存储 Chunk 列表：向量化入 ES + 结构化入 MySQL
     *
     * @param chunks     法律条款切片
     * @param datasetId  所属知识库ID
     * @param fileId     来源文件ID
     * @param domain     法律领域
     * @return 实际写入的切片数
     */
    int store(List<LegalChunk> chunks, Long datasetId, Long fileId, String domain);
}
