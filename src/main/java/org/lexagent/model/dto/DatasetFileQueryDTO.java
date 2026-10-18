package org.lexagent.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文件分页查询参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetFileQueryDTO {

    @NotNull(message = "知识库ID不能为空")
    private Long datasetId;

    private String status;
    private String fileName;
}
