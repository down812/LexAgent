package org.lexagent.service;

import org.lexagent.model.vo.LegalSearchResult;

import java.util.List;

/**
 * 法律知识检索服务（供 Legal Search Agent 调用）
 * 输入问题 → 向量化 → ES 最近邻检索 → 返回 Top-K 法律条款
 */
public interface KnowledgeSearchService {

    /**
     * 语义检索相关法律条款
     *
     * @param query     用户问题或案件检索 query
     * @param datasetId 限定知识库（可为 null，表示全局检索）
     * @param topK      返回条数
     * @return 法律条款检索结果列表
     */
    List<LegalSearchResult> search(String query, Long datasetId, int topK);
}
