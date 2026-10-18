package org.lexagent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.model.vo.LegalSearchResult;
import org.lexagent.service.KnowledgeSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 法律知识检索服务实现
 *
 * 流程：用户问题 → EmbeddingModel 向量化 → Elasticsearch 最近邻检索 → Top-K 法律条款
 * 检索结果携带 metadata（法律名称/条款编号/章节/领域），供后续 Legal Search Agent 使用
 */
@Slf4j
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    @Autowired
    private VectorStore vectorStore;

    @Override
    public List<LegalSearchResult> search(String query, Long datasetId, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (topK <= 0) {
            topK = 5;
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                // 相似度阈值，越低召回越多（调试期设低值保证有结果）
                .similarityThreshold(0.1);

        // 可选：按知识库过滤（ES VectorStore 支持 metadata 过滤表达式）
        if (datasetId != null) {
            builder.filterExpression(RagConstant.META_DATASET_ID + " == " + datasetId);
        }

        List<Document> documents = vectorStore.similaritySearch(builder.build());
        if (documents == null || documents.isEmpty()) {
            log.info("检索无结果：query={}", query);
            return new ArrayList<>();
        }

        List<LegalSearchResult> results = new ArrayList<>();
        for (Document doc : documents) {
            Map<String, Object> meta = doc.getMetadata();
            LegalSearchResult result = LegalSearchResult.builder()
                    .lawName(getStr(meta, RagConstant.META_LAW))
                    .articleNo(getStr(meta, RagConstant.META_ARTICLE))
                    .chapter(getStr(meta, RagConstant.META_CHAPTER))
                    .domain(getStr(meta, RagConstant.META_DOMAIN))
                    .content(doc.getText())
                    .score(getDouble(meta, "score"))
                    .build();
            results.add(result);
        }

        log.info("知识检索完成：query={}，命中 {} 条", query, results.size());
        return results;
    }

    private String getStr(Map<String, Object> meta, String key) {
        Object v = meta == null ? null : meta.get(key);
        return v == null ? null : v.toString();
    }

    private Double getDouble(Map<String, Object> meta, String key) {
        Object v = meta == null ? null : meta.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
