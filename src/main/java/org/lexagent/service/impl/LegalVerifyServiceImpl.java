package org.lexagent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.text.TextSimilarity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.mapper.LegalArticleMapper;
import org.lexagent.mapper.LegalLawMapper;
import org.lexagent.model.entity.LegalArticle;
import org.lexagent.model.entity.LegalLaw;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.service.LegalVerifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 法律条款真实性校验实现
 *
 * 校验逻辑：
 *   1. 按法律名查 legal_laws → 拿到 law_id 列表
 *   2. 按 law_id + article_no 查 legal_articles → 是否存在对应条款
 *   3. 若存在，比对内容相似度（防向量漂移的假条款）
 *
 * 结果分级：
 *   verified=true  + verifyDetail="法律名与条款号均匹配"  → 高可信
 *   verified=true  + verifyDetail="法律名匹配，条款号模糊匹配" → 中可信
 *   verified=false + verifyDetail="未找到对应法律条款" → 疑似幻觉
 */
@Slf4j
@Service
public class LegalVerifyServiceImpl implements LegalVerifyService {

    @Autowired
    private LegalLawMapper legalLawMapper;

    @Autowired
    private LegalArticleMapper legalArticleMapper;

    @Override
    public void verify(List<HybridSearchResult> results, Long datasetId) {
        if (results == null || results.isEmpty()) {
            return;
        }

        // 批量预加载涉及的法律（按 law_name 去重查询，减少 DB 往返）
        Map<String, List<LegalLaw>> lawCache = new HashMap<>();
        for (HybridSearchResult r : results) {
            if (StrUtil.isNotBlank(r.getLawName()) && !lawCache.containsKey(r.getLawName())) {
                LambdaQueryWrapper<LegalLaw> qw = new LambdaQueryWrapper<LegalLaw>()
                        .eq(LegalLaw::getLawName, r.getLawName());
                if (datasetId != null) {
                    qw.eq(LegalLaw::getDatasetId, datasetId);
                }
                lawCache.put(r.getLawName(), legalLawMapper.selectList(qw));
            }
        }

        int verifiedCount = 0;
        for (HybridSearchResult r : results) {
            String lawName = r.getLawName();
            String articleNo = r.getArticleNo();

            if (StrUtil.isBlank(lawName) || StrUtil.isBlank(articleNo)) {
                r.setVerified(false);
                r.setVerifyDetail("法律名或条款号缺失，无法校验");
                continue;
            }

            List<LegalLaw> laws = lawCache.getOrDefault(lawName, new ArrayList<>());
            if (laws.isEmpty()) {
                r.setVerified(false);
                r.setVerifyDetail("MySQL 中不存在法律《" + lawName + "》");
                continue;
            }

            // 在命中的法律下查条款号（先精确，后模糊包含）
            List<Long> lawIds = laws.stream().map(LegalLaw::getId).toList();
            LegalArticle article = findArticle(lawIds, articleNo, r.getContent());

            if (article == null) {
                r.setVerified(false);
                r.setVerifyDetail("未找到《" + lawName + "》" + articleNo + "，疑似幻觉条款");
                continue;
            }

            // 内容相似度校验（防向量漂移：法律名和条款号对上了，但内容严重不一致）
            double sim = contentSimilarity(r.getContent(), article.getContent());
            if (sim < 0.3) {
                r.setVerified(false);
                r.setVerifyDetail("条款号匹配但内容相似度仅 " + String.format("%.2f", sim) + "，疑似内容漂移");
                r.setArticleId(article.getId());
                continue;
            }

            r.setVerified(true);
            r.setArticleId(article.getId());
            r.setVerifyDetail(sim >= 0.7
                    ? "法律名与条款号精确匹配，内容一致"
                    : "法律名与条款号匹配，内容相似度 " + String.format("%.2f", sim));
            verifiedCount++;
        }

        log.info("条款校验完成：共 {} 条，通过 {} 条", results.size(), verifiedCount);
    }

    /**
     * 在指定法律下查找条款：先精确匹配 article_no，找不到再模糊包含
     */
    private LegalArticle findArticle(List<Long> lawIds, String articleNo, String content) {
        // 精确匹配
        List<LegalArticle> exact = legalArticleMapper.selectList(
                new LambdaQueryWrapper<LegalArticle>()
                        .in(LegalArticle::getLawId, lawIds)
                        .eq(LegalArticle::getArticleNo, articleNo));
        if (!exact.isEmpty()) {
            return exact.get(0);
        }

        // 模糊包含（如"第四十条"匹配"第四十条第一款"）
        List<LegalArticle> fuzzy = legalArticleMapper.selectList(
                new LambdaQueryWrapper<LegalArticle>()
                        .in(LegalArticle::getLawId, lawIds)
                        .like(LegalArticle::getArticleNo, articleNo));
        if (!fuzzy.isEmpty()) {
            return fuzzy.get(0);
        }

        // 条款号反向包含（用户检索出的 article_no 可能更短，DB 里是完整编号）
        for (Long lawId : lawIds) {
            List<LegalArticle> reverse = legalArticleMapper.selectList(
                    new LambdaQueryWrapper<LegalArticle>()
                            .eq(LegalArticle::getLawId, lawId)
                            .like(LegalArticle::getArticleNo, articleNo));
            if (!reverse.isEmpty()) {
                return reverse.get(0);
            }
        }
        return null;
    }

    /**
     * 内容相似度：基于 Jaccard 字符 n-gram 的相似度（0-1）
     * 简单高效，适合做内容漂移检测
     */
    private double contentSimilarity(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) {
            return 0.0;
        }
        // 取较短文本长度的较干净子串做 n-gram Jaccard
        String shortA = a.length() > 200 ? a.substring(0, 200) : a;
        String shortB = b.length() > 200 ? b.substring(0, 200) : b;
        return TextSimilarity.similar(shortA, shortB) / 100.0;
    }
}
