package org.lexagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 法律条款切片（结构化中间产物）
 * 一个 LegalChunk 对应一条法律条款，最终写入向量库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalChunk {

    /** 法律名称，如《中华人民共和国劳动合同法》 */
    private String lawName;

    /** 所属章节，如"第三章 劳动合同的订立" */
    private String chapter;

    /** 条款编号，如"第四十条" */
    private String articleNo;

    /** 条款正文内容 */
    private String content;

    /** 法律领域，如"劳动法"（来自 dataset） */
    private String domain;
}
