package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 法律知识库响应数据（含文件数量统计）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetVO {

    @Schema(description = "知识库ID")
    private Long id;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "法律领域")
    private String domain;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "文件数量")
    private Long fileCount;

    @Schema(description = "创建时间")
    private Date createdAt;
}
