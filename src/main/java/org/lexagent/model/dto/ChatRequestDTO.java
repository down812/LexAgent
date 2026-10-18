package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 法律问诊请求 DTO
 */
@Data
public class ChatRequestDTO {

    @Schema(description = "会话ID（前端生成，为空则后端生成UUID）")
    private String conversationId;

    @Schema(description = "用户法律问题描述", example = "我在公司工作两年，公司突然辞退我，没有提前通知，也没有补偿。")
    @NotBlank(message = "问题描述不能为空")
    @Size(max = 2000, message = "问题描述不能超过2000字")
    private String query;
}
