package org.lexagent.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.lexagent.context.LoginContext;
import org.lexagent.exception.BusinessException;
import org.lexagent.model.entity.AnswerFeedback;
import org.lexagent.result.Result;
import org.lexagent.service.FeedbackService;

/**
 * 回答评价接口（V2.0 11.6 回答评价入口 / EVAL-001 EVAL-002）
 * 提交/修改评价与本人评价查询；管理员统计接口在质量评估模块扩展
 */
@Tag(name = "回答评价")
@RestController
@Slf4j
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @Operation(summary = "提交回答评价（EVAL-001）",
            description = "rating=1 有帮助 / 0 没帮助；可附原因与文字反馈；重复提交视为修改")
    public Result<AnswerFeedback> submit(@Validated @RequestBody FeedbackDTO dto) {
        Long userId = requireUser();
        return Result.success(feedbackService.submit(userId, dto.getMessageId(),
                dto.getRating(), dto.getReason(), dto.getContent()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改评价（EVAL-002）")
    public Result<AnswerFeedback> update(@PathVariable Long id,
                                         @Validated @RequestBody FeedbackDTO dto) {
        Long userId = requireUser();
        return Result.success(feedbackService.update(userId, id,
                dto.getRating(), dto.getReason(), dto.getContent()));
    }

    @GetMapping("/my")
    @Operation(summary = "查询本人对某条回答的评价", description = "用于评价状态回显；无评价返回 data=null")
    public Result<AnswerFeedback> getMy(@RequestParam Long messageId) {
        Long userId = requireUser();
        return Result.success(feedbackService.getMy(userId, messageId));
    }

    // ===================== 管理员统计（11.7） =====================

    @PostMapping("/page")
    @Operation(summary = "分页查询反馈（管理员）", description = "支持按评价结果/差评原因筛选，含回答内容摘要")
    public Result<org.lexagent.result.PageResult<org.lexagent.model.vo.FeedbackPageVO>> queryPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestBody(required = false) org.lexagent.model.dto.FeedbackQueryDTO queryDTO) {
        checkAdmin();
        return Result.success(feedbackService.queryPage(pageNum, pageSize, queryDTO));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取评价统计（管理员）",
            description = "反馈总数/好评率/差评原因分布/近14天回答质量变化趋势")
    public Result<org.lexagent.model.vo.FeedbackStatisticsVO> getStatistics() {
        checkAdmin();
        return Result.success(feedbackService.getStatistics());
    }

    /** 管理员权限校验：type 0(超管)/1(管理员) */
    private void checkAdmin() {
        Integer type = LoginContext.getUserType();
        if (type == null || type > org.lexagent.Enum.UserEnum.ADMIN.getCode()) {
            throw new org.lexagent.exception.PermissionDeniedException(
                    org.lexagent.constants.RagConstant.NO_PERMISSION_ADMIN_REQUIRED);
        }
    }

    private Long requireUser() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        return userId;
    }

    /**
     * 评价请求体
     */
    @Data
    public static class FeedbackDTO {
        @NotNull(message = "回答消息ID不能为空")
        private Long messageId;

        @NotNull(message = "评价结果不能为空")
        @Min(value = 0, message = "评价结果仅支持 0/1")
        @Max(value = 1, message = "评价结果仅支持 0/1")
        private Integer rating;

        @jakarta.validation.constraints.Size(max = 100, message = "评价原因不超过100字")
        private String reason;

        @jakarta.validation.constraints.Size(max = 2000, message = "文字反馈不超过2000字")
        private String content;
    }
}
