package org.lexagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建法律知识库请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetCreateDTO {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    @NotBlank(message = "法律领域不能为空")
    private String domain;

    private String description;
}
