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
 * 知识库文件记录实体
 * 对应 dataset_file 表：记录上传的法律文件及其解析/向量化处理状态
 */
@TableName(value = "dataset_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetFile implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属知识库ID")
    @TableField(value = "dataset_id")
    private Long datasetId;

    @Schema(description = "文件名")
    @TableField(value = "file_name")
    private String fileName;

    @Schema(description = "文件类型(PDF/DOCX/TXT/MD/IMAGE)")
    @TableField(value = "file_type")
    private String fileType;

    @Schema(description = "存储路径")
    @TableField(value = "file_path")
    private String filePath;

    @Schema(description = "处理状态(WAITING/PROCESSING/SUCCESS/FAILED)")
    @TableField(value = "status")
    private String status;

    @Schema(description = "切分出的Chunk数量")
    @TableField(value = "chunk_count")
    private Integer chunkCount;

    @Schema(description = "失败原因")
    @TableField(value = "error_msg")
    private String errorMsg;

    @Schema(description = "上传时间")
    @TableField(value = "upload_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date uploadTime;

    @Schema(description = "处理完成时间")
    @TableField(value = "process_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date processTime;
}
