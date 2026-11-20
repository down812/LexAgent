package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 聊天历史消息 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "角色(user/assistant)")
    private String role;

    @Schema(description = "消息内容（user 为纯文本字符串；assistant 为 FinalLegalAnswer 结构化对象，无需二次 JSON.parse）")
    private Object content;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
