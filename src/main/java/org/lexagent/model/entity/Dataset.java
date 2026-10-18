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
 * 法律知识库分类实体
 * 对应 dataset 表：管理不同类型法律知识（劳动法/消费者权益/民事纠纷 等）
 */
@TableName(value = "dataset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dataset implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "知识库ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "知识库名称")
    @TableField(value = "name")
    private String name;

    @Schema(description = "法律领域(劳动法/消费者权益/民事纠纷)")
    @TableField(value = "domain")
    private String domain;

    @Schema(description = "知识库描述")
    @TableField(value = "description")
    private String description;

    @Schema(description = "创建时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
