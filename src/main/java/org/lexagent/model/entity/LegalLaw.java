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
 * 法律信息实体
 * 对应 legal_laws 表：法律级元信息（向量数据存于 Elasticsearch，此表做结构化与校验）
 */
@TableName(value = "legal_laws")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalLaw implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "法律ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "法律名称")
    @TableField(value = "law_name")
    private String lawName;

    @Schema(description = "法律领域")
    @TableField(value = "domain")
    private String domain;

    @Schema(description = "生效状态(有效/废止/已修订)")
    @TableField(value = "status")
    private String status;

    @Schema(description = "来源知识库ID")
    @TableField(value = "dataset_id")
    private Long datasetId;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
