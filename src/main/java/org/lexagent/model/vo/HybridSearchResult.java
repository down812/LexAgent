package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 法律知识混合检索结果（阶段三）
 *
 * 相比基础 LegalSearchResult，增加：
 *   - vectorScore   向量相似度分数
 *   - keywordScore  关键词 BM25 分数
 *   - rerankScore   重排后最终综合分数
 *   - verified      条款真实性校验结果（true=MySQL 中存在对应条款）
 *   - verifyDetail  校验详情（法律名/条款号是否匹配）
 *   - matchType     命中类型（VECTOR/KEYWORD/HYBRID）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HybridSearchResult {

    @Schema(description = "法律名称")
    private String lawName;

    @Schema(description = "条款编号")
    private String articleNo;

    @Schema(description = "所属章节")
    private String chapter;

    @Schema(description = "条款内容")
    private String content;

    @Schema(description = "法律领域")
    private String domain;

    @Schema(description = "向量相似度分数（0-1）")
    private Double vectorScore;

    @Schema(description = "关键词 BM25 分数（归一化后 0-1）")
    private Double keywordScore;

    @Schema(description = "RRF 融合分数")
    private Double rrfScore;

    @Schema(description = "重排后最终综合分数（Rerank）")
    private Double rerankScore;

    @Schema(description = "条款真实性校验：true=MySQL legal_articles 中存在该条款")
    private Boolean verified;

    @Schema(description = "校验详情")
    private String verifyDetail;

    @Schema(description = "命中类型：VECTOR(仅向量)/KEYWORD(仅关键词)/HYBRID(两路命中)")
    private String matchType;

    @Schema(description = "MySQL 条款记录ID（校验通过时填充）")
    private Long articleId;
}
