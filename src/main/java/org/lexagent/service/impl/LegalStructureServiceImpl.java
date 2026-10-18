package org.lexagent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lexagent.model.dto.LegalChunk;
import org.lexagent.service.LegalStructureService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律文本结构化服务实现
 *
 * 法律场景 RAG 与普通 RAG 的核心区别：
 *   - 普通 RAG：按定长（如 500 字）切分
 *   - 法律 RAG：按"条款"切分，保留 法律名称 + 章节 + 条款编号 + 正文 的结构
 *
 * 本实现基于正则识别：
 *   1. 法律名称：文档开头匹配"中华人民共和国XX法"或"XX条例"
 *   2. 章节标题：第X章 标题
 *   3. 条款编号：第X条
 *   4. 条款正文：条款编号之后至下一条款编号之前的文本
 */
@Slf4j
@Service
public class LegalStructureServiceImpl implements LegalStructureService {

    /** 条款编号：第X条（中文数字或阿拉伯数字） */
    private static final Pattern ARTICLE_PATTERN =
            Pattern.compile("第([零一二三四五六七八九十百千万0-9]+)条");

    /** 章节标题：第X章 标题 */
    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile("第([零一二三四五六七八九十百千万0-9]+)章[\\s　]*([^\\n\\r]*)");

    /** 法律名称：中华人民共和国XX法 / XX条例 */
    private static final Pattern LAW_NAME_PATTERN =
            Pattern.compile("(中华人民共和国[\\u4e00-\\u9fa5]+法|[\\u4e00-\\u9fa5]{2,}条例|[\\u4e00-\\u9fa5]{2,}法)");

    @Override
    public List<LegalChunk> structure(String text, String defaultDomain) {
        List<LegalChunk> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 1. 识别法律名称
        String lawName = extractLawName(text);

        // 2. 定位所有"第X条"出现位置
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(text);
        List<int[]> articleSpans = new ArrayList<>();
        List<String> articleNums = new ArrayList<>();
        while (articleMatcher.find()) {
            articleSpans.add(new int[]{articleMatcher.start(), articleMatcher.end()});
            articleNums.add(articleMatcher.group(1));
        }

        if (articleSpans.isEmpty()) {
            // 未识别到条款结构，整篇作为单个切片兜底
            log.warn("未识别到法律条款结构，整篇作为单个切片：{}", lawName);
            chunks.add(LegalChunk.builder()
                    .lawName(lawName)
                    .chapter("全文")
                    .articleNo("全文")
                    .content(text.trim())
                    .domain(defaultDomain)
                    .build());
            return chunks;
        }

        // 3. 定位所有"第X章"位置（用于为条款归属章节）
        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(text);
        List<int[]> chapterSpans = new ArrayList<>();
        List<String> chapterTitles = new ArrayList<>();
        while (chapterMatcher.find()) {
            chapterSpans.add(new int[]{chapterMatcher.start(), chapterMatcher.end()});
            chapterTitles.add("第" + chapterMatcher.group(1) + "章 " + chapterMatcher.group(2).trim());
        }

        // 4. 按条款切分：每条 = 编号 + 正文（至下一条款前）
        for (int i = 0; i < articleSpans.size(); i++) {
            int articleStart = articleSpans.get(i)[0];
            int articleEnd = articleSpans.get(i)[1];
            int contentStart = articleEnd;
            int contentEnd = (i + 1 < articleSpans.size()) ? articleSpans.get(i + 1)[0] : text.length();
            String content = text.substring(contentStart, contentEnd).trim();
            String articleNo = "第" + articleNums.get(i) + "条";

            // 归属章节：取当前位置之前最近的章节标题
            String chapter = "总则";
            for (int j = chapterSpans.size() - 1; j >= 0; j--) {
                if (chapterSpans.get(j)[0] <= articleStart) {
                    chapter = chapterTitles.get(j);
                    break;
                }
            }

            // 跳过空正文条款
            if (content.isEmpty()) {
                continue;
            }

            chunks.add(LegalChunk.builder()
                    .lawName(lawName)
                    .chapter(chapter)
                    .articleNo(articleNo)
                    .content(content)
                    .domain(defaultDomain)
                    .build());
        }

        log.info("法律文本结构化完成：法律={}，切片数={}", lawName, chunks.size());
        return chunks;
    }

    /**
     * 提取法律名称：在文档前 200 字内匹配
     */
    private String extractLawName(String text) {
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        Matcher m = LAW_NAME_PATTERN.matcher(head);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "未知法律";
    }
}
