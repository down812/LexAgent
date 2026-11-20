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
 *   2. 章节标题：第X章 标题（行首匹配）
 *   3. 条款编号：第X条（行首匹配，避免匹配正文中的引用）
 *   4. 条款正文：条款编号之后至下一条款编号之前的文本
 *
 * 【关键修复】条款编号正则改为行首匹配（(?m)^...），避免正文中
 *   "依照本法第四十条的规定"这类引用被误识别为新条款起始位置，
 *   导致条款正文被截断、内容不完整、章节归属错误。
 */
@Slf4j
@Service
public class LegalStructureServiceImpl implements LegalStructureService {

    /** 条款编号（行首匹配）：只匹配每行开头的"第X条"，不匹配正文中的引用 */
    private static final Pattern ARTICLE_PATTERN_LINE_START =
            Pattern.compile("(?m)^[\\s　]*第([零一二三四五六七八九十百千万0-9]+)条");

    /** 条款编号（全局匹配，降级用）：当行首匹配结果过少时使用 */
    private static final Pattern ARTICLE_PATTERN_GLOBAL =
            Pattern.compile("第([零一二三四五六七八九十百千万0-9]+)条");

    /** 章节标题（行首匹配）：第X章 标题 */
    private static final Pattern CHAPTER_PATTERN_LINE_START =
            Pattern.compile("(?m)^[\\s　]*第([零一二三四五六七八九十百千万0-9]+)章[\\s　]*([^\\n\\r]*)");

    /** 法律名称：中华人民共和国XX法 / XX条例 */
    private static final Pattern LAW_NAME_PATTERN =
            Pattern.compile("(中华人民共和国[\\u4e00-\\u9fa5]+法|[\\u4e00-\\u9fa5]{2,}条例|[\\u4e00-\\u9fa5]{2,}法)");

    /** 引用词：匹配位置前面有这些词时，"第X条"是正文引用而非条款起始 */
    private static final String[] REFERENCE_KEYWORDS = {
            "本法", "按照", "依照", "参照", "依据", "根据", "适用", "前条", "前款", "上述", "另有规定"
    };

    /** 行首匹配降级阈值：少于此次数认为文档可能没有换行 */
    private static final int LINE_START_FALLBACK_THRESHOLD = 3;

    @Override
    public List<LegalChunk> structure(String text, String defaultDomain) {
        List<LegalChunk> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 1. 识别法律名称
        String lawName = extractLawName(text);

        // 2. 定位所有"第X条"（行首优先，避免匹配正文引用）
        List<int[]> articleSpans = new ArrayList<>();
        List<String> articleNums = new ArrayList<>();
        Matcher lineStartMatcher = ARTICLE_PATTERN_LINE_START.matcher(text);
        while (lineStartMatcher.find()) {
            articleSpans.add(new int[]{lineStartMatcher.start(), lineStartMatcher.end()});
            articleNums.add(lineStartMatcher.group(1));
        }

        // 降级：行首匹配结果太少（文档可能没有换行），用全局匹配 + 引用词过滤
        if (articleSpans.size() < LINE_START_FALLBACK_THRESHOLD) {
            log.warn("行首匹配仅找到 {} 个条款，降级为全局匹配 + 引用词过滤", articleSpans.size());
            articleSpans.clear();
            articleNums.clear();
            Matcher globalMatcher = ARTICLE_PATTERN_GLOBAL.matcher(text);
            while (globalMatcher.find()) {
                int matchStart = globalMatcher.start();
                // 检查前面 15 个字符是否有引用词
                String before = text.substring(Math.max(0, matchStart - 15), matchStart);
                if (isArticleReference(before)) {
                    continue;  // 跳过正文引用
                }
                articleSpans.add(new int[]{matchStart, globalMatcher.end()});
                articleNums.add(globalMatcher.group(1));
            }
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

        // 3. 定位所有"第X章"位置（行首匹配，用于为条款归属章节）
        Matcher chapterMatcher = CHAPTER_PATTERN_LINE_START.matcher(text);
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
     * 判断"第X条"前面是否有引用词（说明是正文引用而非条款起始）
     */
    private boolean isArticleReference(String before) {
        for (String keyword : REFERENCE_KEYWORDS) {
            if (before.contains(keyword)) {
                return true;
            }
        }
        return false;
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
