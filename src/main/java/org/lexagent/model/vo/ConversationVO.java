package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话列表/详情 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationVO {

    @Schema(description = "主键ID（自增）")
    private Long id;

    @Schema(description = "业务会话ID（前端生成，UUID格式）")
    private String conversationId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "状态(ACTIVE/ARCHIVED)")
    private String status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
