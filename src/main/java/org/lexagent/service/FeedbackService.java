package org.lexagent.service;

import org.lexagent.model.entity.AnswerFeedback;

/**
 * 回答评价服务（V2.0 / EVAL-001 EVAL-002）
 * 用户对 AI 回答提交/修改评价（有帮助/没帮助 + 原因 + 文字反馈）
 * 管理员统计接口在质量评估模块（第六阶段）扩展
 */
public interface FeedbackService {

    /** 提交评价：同一用户同一回答重复提交视为修改（EVAL-002） */
    AnswerFeedback submit(Long userId, Long messageId, Integer rating, String reason, String content);

    /** 修改评价 */
    AnswerFeedback update(Long userId, Long feedbackId, Integer rating, String reason, String content);

    /** 查询当前用户对某条回答的评价（无则返回 null） */
    AnswerFeedback getMy(Long userId, Long messageId);

    /** 管理员分页查询反馈（14.4 /feedback/page），含回答摘要 */
    org.lexagent.result.PageResult<org.lexagent.model.vo.FeedbackPageVO> queryPage(
            Integer pageNum, Integer pageSize, org.lexagent.model.dto.FeedbackQueryDTO queryDTO);

    /** 评价统计（11.7）：总数/好评率/差评原因分布/近14天质量趋势 */
    org.lexagent.model.vo.FeedbackStatisticsVO getStatistics();
}
