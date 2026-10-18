package org.lexagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 法律条款实体
 * 对应 legal_articles 表：按条款切分后的结构化法律条文（与 ES 向量一一对应便于校验）
 */
@TableName(value = "legal_articles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalArticle implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "条款ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属法律ID")
    @TableField(value = "law_id")
    private Long lawId;

    @Schema(description = "条款编号(第四十条)")
    @TableField(value = "article_no")
    private String articleNo;

    @Schema(description = "所属章节")
    @TableField(value = "chapter")
    private String chapter;

    @Schema(description = "条款内容")
    @TableField(value = "content")
    private String content;

    @Schema(description = "来源文件ID")
    @TableField(value = "dataset_file_id")
    private Long datasetFileId;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
