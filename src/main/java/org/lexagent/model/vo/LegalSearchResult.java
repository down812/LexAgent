package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 法律知识检索结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalSearchResult {

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

    @Schema(description = "相似度分数")
    private Double score;
}
