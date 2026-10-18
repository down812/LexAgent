package org.lexagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.service.HybridSearchService;
import org.lexagent.service.LegalVerifyService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 混合检索服务实现（阶段三·论文创新点）
 *
 * 四阶段流水线：
 *   1. 向量召回    —— VectorStore.similaritySearch（语义相似）
 *   2. 关键词召回  —— Elasticsearch match query（精确匹配，含条款号）
 *   3. RRF 融合   —— Reciprocal Rank Fusion，按排名倒数加权
 *   4. Reranker   —— 法律场景多特征加权精细重排
 *   5. Verify     —— MySQL 条款真实性校验（防幻觉）
 *
 * RRF 公式：score = Σ 1/(k + rank_i)，k=60（标准常数）
 *
 * Reranker 加权特征：
 *   - 条款号精确匹配（用户提到"第四十条"，候选含"第四十条"）→ +3.0（法律场景核心特征）
 *   - 法律名匹配 → +1.5
 *   - 关键词覆盖率（query 中的实词在 content 命中的比例）→ ×2.0
 *   - 向量相似度 → ×1.0（基础分）
 *   - 内容长度归一化（过短/过长都轻微惩罚）
 */
@Slf4j
@Service
public class HybridSearchServiceImpl implements HybridSearchService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private LegalVerifyService legalVerifyService;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:legal-knowledge}")
    private String esIndexName;

    /** RRF 常数（标准值 60，越大融合越平滑） */
    private static final int RRF_K = 60;

    /** 向量召回扩大倍数：topK * N 召回，给 rerank 更多候选 */
    private static final int RECALL_MULTIPLIER = 4;

    /** 中文条款号正则：第X条/第X章/第X款/第X项 */
    private static final Pattern ARTICLE_NO_PATTERN =
            Pattern.compile("第[一二三四五六七八九十百千零0-9]+[条章节款项]");

    @Override
    public List<HybridSearchResult> hybridSearch(String query, Long datasetId, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (topK <= 0) {
            topK = 5;
        }

        int recallN = topK * RECALL_MULTIPLIER;
        log.info("Hybrid Search 开始：query=\"{}\" datasetId={} recallN={} topK={}", query, datasetId, recallN, topK);
        long start = System.currentTimeMillis();

        // ========== 1. 向量召回 ==========
        List<HybridSearchResult> vectorHits = vectorSearch(query, datasetId, recallN);
        log.info("向量召回：{} 条", vectorHits.size());

        // ========== 2. 关键词召回 ==========
        List<HybridSearchResult> keywordHits = keywordSearch(query, datasetId, recallN);
        log.info("关键词召回：{} 条", keywordHits.size());

        // ========== 3. RRF 融合 ==========
        List<HybridSearchResult> merged = rrfFusion(vectorHits, keywordHits);
        log.info("RRF 融合后：{} 条", merged.size());

        // ========== 4. Reranker 多特征加权 ==========
        List<HybridSearchResult> reranked = rerank(query, merged, topK);
        log.info("Rerank 完成：取 Top-{}", reranked.size());

        // ========== 5. 条款校验 ==========
        legalVerifyService.verify(reranked, datasetId);

        long cost = System.currentTimeMillis() - start;
        log.info("Hybrid Search 完成：耗时={}ms，返回 {} 条", cost, reranked.size());
        return reranked;
    }

    // ===================== 1. 向量召回 =====================

    private List<HybridSearchResult> vectorSearch(String query, Long datasetId, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.05);

        if (datasetId != null) {
            builder.filterExpression(RagConstant.META_DATASET_ID + " == " + datasetId);
        }

        List<Document> docs = vectorStore.similaritySearch(builder.build());
        if (docs == null || docs.isEmpty()) {
            return new ArrayList<>();
        }

        List<HybridSearchResult> results = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            Map<String, Object> meta = doc.getMetadata();
            results.add(HybridSearchResult.builder()
                    .lawName(getStr(meta, RagConstant.META_LAW))
                    .articleNo(getStr(meta, RagConstant.META_ARTICLE))
                    .chapter(getStr(meta, RagConstant.META_CHAPTER))
                    .domain(getStr(meta, RagConstant.META_DOMAIN))
                    .content(doc.getText())
                    .vectorScore(getDouble(meta, "distance") != null
                            ? 1 - getDouble(meta, "distance") : getDouble(meta, "score"))
                    .matchType("VECTOR")
                    .build());
        }
        return results;
    }

    // ===================== 2. 关键词召回（ES match query） =====================

    private List<HybridSearchResult> keywordSearch(String query, Long datasetId, int topK) {
        try {
            // 构建 ES 查询：关键词 match + 可选 datasetId 过滤
            final Query finalQuery;
            if (datasetId != null) {
                finalQuery = Query.of(q -> q.bool(b -> b
                        .must(Query.of(m -> m.match(mm -> mm.field("text").query(query))))
                        .filter(Query.of(f -> f.term(t -> t.field(RagConstant.META_DATASET_ID).value(datasetId))))));
            } else {
                finalQuery = Query.of(q -> q.match(m -> m.field("text").query(query)));
            }

            SearchResponse<Map> resp = esClient.search(s -> s
                    .index(esIndexName)
                    .query(finalQuery)
                    .size(topK), Map.class);

            List<HybridSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                Map<String, Object> src = hit.source() != null ? hit.source() : new HashMap<>();
                // Spring AI VectorStore 存的文档 text 在 "text" 或 "content" 字段
                String text = getStr(src, "text");
                if (text == null) text = getStr(src, "content");
                Map<String, Object> meta = getMap(src, "metadata");

                double score = hit.score() != null ? hit.score().doubleValue() : 0.0;
                results.add(HybridSearchResult.builder()
                        .lawName(getStr(meta, RagConstant.META_LAW))
                        .articleNo(getStr(meta, RagConstant.META_ARTICLE))
                        .chapter(getStr(meta, RagConstant.META_CHAPTER))
                        .domain(getStr(meta, RagConstant.META_DOMAIN))
                        .content(text)
                        .keywordScore(score)
                        .matchType("KEYWORD")
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.warn("关键词检索失败（ES 可能未就绪或索引不存在），仅用向量结果: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ===================== 3. RRF 融合排序 =====================

    /**
     * Reciprocal Rank Fusion
     * 对两路结果按各自排名计算倒数得分：1/(k+rank)
     * 相同文档（按 law_name + article_no 去重）累加得分
     */
    private List<HybridSearchResult> rrfFusion(List<HybridSearchResult> vectorHits,
                                               List<HybridSearchResult> keywordHits) {
        // 用 law_name + article_no 作为文档唯一键（同一条款可能两路都召回）
        Map<String, HybridSearchResult> merged = new LinkedHashMap<>();

        // 向量路
        for (int i = 0; i < vectorHits.size(); i++) {
            String key = docKey(vectorHits.get(i));
            HybridSearchResult existing = merged.get(key);
            double rrf = 1.0 / (RRF_K + i + 1);
            if (existing == null) {
                HybridSearchResult r = vectorHits.get(i);
                r.setRrfScore(rrf);
                merged.put(key, r);
            } else {
                existing.setRrfScore((existing.getRrfScore() == null ? 0 : existing.getRrfScore()) + rrf);
                // 保留向量分数
                if (existing.getVectorScore() == null) {
                    existing.setVectorScore(vectorHits.get(i).getVectorScore());
                }
                existing.setMatchType("HYBRID");
            }
        }

        // 关键词路
        for (int i = 0; i < keywordHits.size(); i++) {
            String key = docKey(keywordHits.get(i));
            HybridSearchResult existing = merged.get(key);
            double rrf = 1.0 / (RRF_K + i + 1);
            if (existing == null) {
                HybridSearchResult r = keywordHits.get(i);
                r.setRrfScore(rrf);
                merged.put(key, r);
            } else {
                existing.setRrfScore((existing.getRrfScore() == null ? 0 : existing.getRrfScore()) + rrf);
                if (existing.getKeywordScore() == null) {
                    existing.setKeywordScore(keywordHits.get(i).getKeywordScore());
                }
                existing.setMatchType("HYBRID");
            }
        }

        // 按 RRF 分数降序
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(
                        (HybridSearchResult r) -> r.getRrfScore() == null ? 0 : r.getRrfScore())
                        .reversed())
                .collect(Collectors.toList());
    }

    // ===================== 4. Reranker 多特征加权 =====================

    /**
     * 法律场景多特征加权重排
     *
     * 综合分 = baseScore + featureBonus
     *   baseScore = vectorScore * 0.4 + rrfScore * 2.0    （归一化的基础分）
     *   featureBonus：
     *     + 条款号精确匹配   ×3.0    （用户提到"第四十条"，候选含"第四十条"）
     *     + 法律名匹配       ×1.5    （query 含法律名且候选属于该法）
     *     + 关键词覆盖率     ×2.0    （query 实词在 content 命中比例）
     *     ± 内容长度归一化   ×0.3    （过短惩罚）
     */
    private List<HybridSearchResult> rerank(String query, List<HybridSearchResult> candidates, int topK) {
        // 从 query 提取条款号（法律查询强依赖条款号，是最重要的特征）
        Set<String> queryArticleNos = extractArticleNos(query);
        // 从 query 提取实词（去除停用词和短词后的关键 token）
        Set<String> queryKeywords = extractKeywords(query);

        for (HybridSearchResult r : candidates) {
            double baseScore = 0;
            if (r.getVectorScore() != null) baseScore += r.getVectorScore() * 0.4;
            if (r.getRrfScore() != null) baseScore += r.getRrfScore() * 2.0;

            double bonus = 0;

            // 特征1：条款号精确匹配（法律场景核心特征）
            if (!queryArticleNos.isEmpty() && StrUtil.isNotBlank(r.getArticleNo())) {
                for (String ano : queryArticleNos) {
                    if (r.getArticleNo().contains(ano) || ano.contains(r.getArticleNo())) {
                        bonus += 3.0;
                        break;
                    }
                }
            }

            // 特征2：法律名匹配
            if (StrUtil.isNotBlank(r.getLawName()) && query.contains(r.getLawName())) {
                bonus += 1.5;
            }

            // 特征3：关键词覆盖率
            if (!queryKeywords.isEmpty() && StrUtil.isNotBlank(r.getContent())) {
                String content = r.getContent();
                int hit = 0;
                for (String kw : queryKeywords) {
                    if (content.contains(kw)) hit++;
                }
                double coverage = (double) hit / queryKeywords.size();
                bonus += coverage * 2.0;
            }

            // 特征4：内容长度归一化（极短内容信息量不足，轻微惩罚）
            int len = StrUtil.length(r.getContent());
            if (len < 20) {
                bonus -= 0.3;
            }

            r.setRerankScore(baseScore + bonus);
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (HybridSearchResult r) -> r.getRerankScore() == null ? 0 : r.getRerankScore())
                        .reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    // ===================== 工具方法 =====================

    private String docKey(HybridSearchResult r) {
        String law = r.getLawName() == null ? "" : r.getLawName();
        String art = r.getArticleNo() == null ? "" : r.getArticleNo();
        // 加 content 前缀做更细去重（同法同条可能不同切片）
        String contentPrefix = r.getContent() == null ? "" :
                r.getContent().length() > 30 ? r.getContent().substring(0, 30) : r.getContent();
        return law + "|" + art + "|" + contentPrefix;
    }

    private Set<String> extractArticleNos(String text) {
        Set<String> nos = new HashSet<>();
        Matcher m = ARTICLE_NO_PATTERN.matcher(text);
        while (m.find()) {
            nos.add(m.group());
        }
        return nos;
    }

    /**
     * 提取查询关键词（中文 2-gram + 停用词过滤）
     * 简单方案：取所有长度 >= 2 的连续中文字符段，过滤常见停用词
     */
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "上", "也", "很",
            "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那",
            "什么", "怎么", "可以", "应该", "需要", "如果", "但是", "因为", "所以", "还是",
            "一个", "这种", "那种", "关于", "对于", "根据", "按照", "以及", "或者", "并且"
    );

    private Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();
        // 中文/英文连续片段切分
        String cleaned = query.replaceAll("[\\s\\p{Punct}]+", " ").trim();
        for (String token : cleaned.split(" ")) {
            if (token.length() >= 2 && !STOPWORDS.contains(token)) {
                keywords.add(token);
            }
        }
        // 额外：从 query 中抽取所有 2-gram 中文片段，增强召回
        for (int i = 0; i + 2 <= query.length(); i++) {
            char c1 = query.charAt(i);
            char c2 = query.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                String gram = query.substring(i, i + 2);
                if (!STOPWORDS.contains(gram)) {
                    keywords.add(gram);
                }
            }
        }
        return keywords;
    }

    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private String getStr(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private Double getDouble(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }
}
