package org.lexagent.service;

import org.lexagent.model.vo.HybridSearchResult;

import java.util.List;

/**
 * 混合检索服务（阶段三·论文创新点）
 *
 * 流程：
 *   1. 向量搜索（VectorStore similaritySearch）—— 语义召回
 *   2. 关键词搜索（Elasticsearch match query）—— 精确召回（条款号/法律名等）
 *   3. RRF 融合排序 —— 两路结果按排名倒数加权融合
 *   4. Reranker 多特征加权 —— 法律场景特征精细重排
 *   5. 条款校验 —— MySQL legal_articles 真实性核验（防幻觉）
 *
 * 法律查询特点：用户常带具体条款号（"第四十七条赔偿"），
 * 纯向量搜索对精确关键词不敏感，Hybrid Search 能显著提升召回率。
 */
public interface HybridSearchService {

    /**
     * 混合检索 + 重排 + 校验全链路
     *
     * @param query     用户问题
     * @param datasetId 限定知识库（null 表示全局）
     * @param topK      最终返回条数
     * @return 重排校验后的结果列表
     */
    List<HybridSearchResult> hybridSearch(String query, Long datasetId, int topK);
}
