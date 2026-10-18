package org.lexagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库分页查询参数（名称模糊 + 领域精确）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetQueryDTO {

    private String name;
    private String domain;
}
