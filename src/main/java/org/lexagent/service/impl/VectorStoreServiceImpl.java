package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.mapper.LegalArticleMapper;
import org.lexagent.mapper.LegalLawMapper;
import org.lexagent.model.dto.LegalChunk;
import org.lexagent.model.entity.LegalArticle;
import org.lexagent.model.entity.LegalLaw;
import org.lexagent.service.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化存储服务实现
 *
 * 存储双写：
 *   1. Elasticsearch：text + metadata + vector（由 VectorStore 调 EmbeddingModel 自动向量化）
 *   2. MySQL：legal_laws / legal_articles 结构化条款（供 Reviewer Agent 校验法律真实性）
 */
@Slf4j
@Service
public class VectorStoreServiceImpl implements VectorStoreService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private LegalLawMapper legalLawMapper;

    @Autowired
    private LegalArticleMapper legalArticleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int store(List<LegalChunk> chunks, Long datasetId, Long fileId, String domain) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        List<Document> documents = new ArrayList<>();
        for (LegalChunk chunk : chunks) {
            // 1. 构建向量库文档：文本 = 法律名称 + 条款编号 + 正文（提升检索命中率）
            String text = chunk.getLawName() + " " + chunk.getArticleNo() + "\n" + chunk.getContent();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(RagConstant.META_LAW, chunk.getLawName());
            metadata.put(RagConstant.META_ARTICLE, chunk.getArticleNo());
            metadata.put(RagConstant.META_CHAPTER, chunk.getChapter());
            metadata.put(RagConstant.META_DOMAIN, chunk.getDomain());
            metadata.put(RagConstant.META_DATASET_ID, datasetId);
            metadata.put(RagConstant.META_FILE_ID, fileId);
            documents.add(new Document(text, metadata));

            // 2. 同步结构化条款到 MySQL
            upsertLawAndArticle(chunk, datasetId, fileId);
        }

        // 3. 分批向量化并写入 Elasticsearch（DashScope Embedding API 单次上限 25 条）
        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            log.info("向量化写入批次：{}/{}，本批 {} 条", (i / batchSize + 1),
                    (documents.size() + batchSize - 1) / batchSize, batch.size());
        }

        log.info("向量化存储完成：fileId={}，切片数={}，已写入 ES 与 MySQL", fileId, documents.size());
        return documents.size();
    }

    /**
     * 写入/复用法律信息，并落库条款记录
     */
    private void upsertLawAndArticle(LegalChunk chunk, Long datasetId, Long fileId) {
        // 法律信息：按 law_name 唯一，存在复用，不存在新建
        LegalLaw law = legalLawMapper.selectOne(
                new LambdaQueryWrapper<LegalLaw>().eq(LegalLaw::getLawName, chunk.getLawName()));
        if (law == null) {
            law = LegalLaw.builder()
                    .lawName(chunk.getLawName())
                    .domain(chunk.getDomain())
                    .status("有效")
                    .datasetId(datasetId)
                    .createdAt(DateTime.now())
                    .build();
            legalLawMapper.insert(law);
        }

        LegalArticle article = LegalArticle.builder()
                .lawId(law.getId())
                .articleNo(chunk.getArticleNo())
                .chapter(chunk.getChapter())
                .content(chunk.getContent())
                .datasetFileId(fileId)
                .createdAt(DateTime.now())
                .build();
        legalArticleMapper.insert(article);
    }
}