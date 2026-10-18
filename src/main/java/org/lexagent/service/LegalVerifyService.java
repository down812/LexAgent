package org.lexagent.service;

import org.lexagent.model.vo.HybridSearchResult;

import java.util.List;

/**
 * 法律条款真实性校验服务（阶段三·防幻觉）
 *
 * RAG 检索可能返回"似是而非"的条款（向量近但实际不存在），
 * 本服务将检索结果与 MySQL legal_articles 结构化条款比对：
 *   - 法律名 + 条款号 能否在 MySQL 中找到对应记录
 *   - 内容是否高度一致（防向量漂移产生的假条款）
 *
 * 这是法律场景特有的防幻觉机制，论文创新点之一。
 */
public interface LegalVerifyService {

    /**
     * 批量校验检索结果的条款真实性
     * 直接修改 results 中每个元素的 verified / verifyDetail / articleId 字段
     *
     * @param results 检索结果列表
     * @param datasetId 限定知识库（null 表示全局）
     */
    void verify(List<HybridSearchResult> results, Long datasetId);
}
