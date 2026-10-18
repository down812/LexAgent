package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识库文件记录响应数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetFileVO {

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long datasetId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "处理状态")
    private String status;

    @Schema(description = "切分Chunk数量")
    private Integer chunkCount;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "上传时间")
    private Date uploadTime;

    @Schema(description = "处理完成时间")
    private Date processTime;
}
