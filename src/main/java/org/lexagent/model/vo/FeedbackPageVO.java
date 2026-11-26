package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 反馈记录 VO（管理员分页查询）
 * 含回答内容摘要，便于管理员定位问题回答
 */
@Data
@Schema(description = "反馈记录")
public class FeedbackPageVO {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "评价用户ID")
    private Long userId;

    @Schema(description = "回答消息ID")
    private Long messageId;

    @Schema(description = "评价结果(1有帮助/0没帮助)")
    private Integer rating;

    @Schema(description = "评价原因")
    private String reason;

    @Schema(description = "文字反馈")
    private String content;

    @Schema(description = "回答内容摘要（前120字）")
    private String answerExcerpt;

    @Schema(description = "评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
