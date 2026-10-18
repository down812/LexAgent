package org.lexagent.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改法律知识库请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetUpdateDTO {

    @NotNull(message = "知识库ID不能为空")
    private Long id;

    private String name;
    private String domain;
    private String description;
}
