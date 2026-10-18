package org.lexagent.service;

import org.lexagent.model.dto.LegalChunk;

import java.util.List;

/**
 * 法律文本结构化服务
 * 识别法律名称 / 章节 / 条款编号 / 正文，按条款切分为 LegalChunk 列表
 */
public interface LegalStructureService {

    /**
     * 将纯文本法律文件结构化切分为条款级 Chunk
     *
     * @param text          解析后的法律纯文本
     * @param defaultDomain 默认法律领域（来自所属知识库）
     * @return 条款切片列表
     */
    List<LegalChunk> structure(String text, String defaultDomain);
}
