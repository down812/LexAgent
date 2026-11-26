package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.mapper.AnswerFeedbackMapper;
import org.lexagent.mapper.ConversationMapper;
import org.lexagent.mapper.MessageMapper;
import org.lexagent.model.entity.AnswerFeedback;
import org.lexagent.model.entity.Conversation;
import org.lexagent.model.entity.Message;
import org.lexagent.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回答评价服务实现（V2.0）
 *
 * 权限：评价的消息必须属于当前用户自己的会话（通过 message → conversation → user_id 校验）。
 * 唯一性：user_id + message_id 唯一，重复提交走更新逻辑（EVAL-002 修改评价）。
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Autowired
    private AnswerFeedbackMapper answerFeedbackMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Override
    public AnswerFeedback submit(Long userId, Long messageId, Integer rating, String reason, String content) {
        checkRating(rating);
        checkMessageOwnership(userId, messageId);

        AnswerFeedback existing = answerFeedbackMapper.selectOne(
                new LambdaQueryWrapper<AnswerFeedback>()
                        .eq(AnswerFeedback::getUserId, userId)
                        .eq(AnswerFeedback::getMessageId, messageId));
        if (existing != null) {
            // 已有评价 → 修改（EVAL-002）
            existing.setRating(rating);
            existing.setReason(reason);
            existing.setContent(content);
            existing.setUpdatedAt(DateTime.now());
            answerFeedbackMapper.updateById(existing);
            log.info("用户[{}]修改回答[{}]评价：rating={}", userId, messageId, rating);
            return existing;
        }

        AnswerFeedback feedback = AnswerFeedback.builder()
                .userId(userId)
                .messageId(messageId)
                .rating(rating)
                .reason(reason)
                .content(content)
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        answerFeedbackMapper.insert(feedback);
        log.info("用户[{}]提交回答[{}]评价：rating={}", userId, messageId, rating);
        return feedback;
    }

    @Override
    public AnswerFeedback update(Long userId, Long feedbackId, Integer rating, String reason, String content) {
        checkRating(rating);
        AnswerFeedback feedback = answerFeedbackMapper.selectById(feedbackId);
        if (feedback == null || !Objects.equals(feedback.getUserId(), userId)) {
            throw new BusinessException("评价不存在或无权修改");
        }
        feedback.setRating(rating);
        feedback.setReason(reason);
        feedback.setContent(content);
        feedback.setUpdatedAt(DateTime.now());
        answerFeedbackMapper.updateById(feedback);
        return feedback;
    }

    @Override
    public AnswerFeedback getMy(Long userId, Long messageId) {
        return answerFeedbackMapper.selectOne(
                new LambdaQueryWrapper<AnswerFeedback>()
                        .eq(AnswerFeedback::getUserId, userId)
                        .eq(AnswerFeedback::getMessageId, messageId));
    }

    @Override
    public org.lexagent.result.PageResult<org.lexagent.model.vo.FeedbackPageVO> queryPage(
            Integer pageNum, Integer pageSize, org.lexagent.model.dto.FeedbackQueryDTO queryDTO) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnswerFeedback> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AnswerFeedback> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO != null) {
            if (queryDTO.getRating() != null) {
                wrapper.eq(AnswerFeedback::getRating, queryDTO.getRating());
            }
            if (queryDTO.getReason() != null && !queryDTO.getReason().trim().isEmpty()) {
                wrapper.eq(AnswerFeedback::getReason, queryDTO.getReason().trim());
            }
        }
        wrapper.orderByDesc(AnswerFeedback::getCreatedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnswerFeedback> result =
                answerFeedbackMapper.selectPage(page, wrapper);

        // 批量取回答内容生成摘要（截断 120 字）
        List<Long> messageIds = result.getRecords().stream()
                .map(AnswerFeedback::getMessageId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, String> excerptMap = new java.util.HashMap<>();
        if (!messageIds.isEmpty()) {
            for (Message m : messageMapper.selectBatchIds(messageIds)) {
                String text = m.getContent();
                if (text != null && text.length() > 120) {
                    text = text.substring(0, 120) + "…";
                }
                excerptMap.put(m.getId(), text);
            }
        }

        List<org.lexagent.model.vo.FeedbackPageVO> voList = result.getRecords().stream()
                .map(f -> {
                    org.lexagent.model.vo.FeedbackPageVO vo = new org.lexagent.model.vo.FeedbackPageVO();
                    vo.setId(f.getId());
                    vo.setUserId(f.getUserId());
                    vo.setMessageId(f.getMessageId());
                    vo.setRating(f.getRating());
                    vo.setReason(f.getReason());
                    vo.setContent(f.getContent());
                    vo.setAnswerExcerpt(excerptMap.get(f.getMessageId()));
                    vo.setCreatedAt(f.getCreatedAt());
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());
        return new org.lexagent.result.PageResult<>(result.getTotal(), result.getPages(), pageNum, pageSize, voList);
    }

    @Override
    public org.lexagent.model.vo.FeedbackStatisticsVO getStatistics() {
        List<AnswerFeedback> all = answerFeedbackMapper.selectList(
                new LambdaQueryWrapper<AnswerFeedback>().orderByAsc(AnswerFeedback::getCreatedAt));

        long total = all.size();
        long helpful = all.stream().filter(f -> f.getRating() != null && f.getRating() == 1).count();
        long notHelpful = total - helpful;
        int goodRate = total == 0 ? 0 : (int) Math.round(helpful * 100.0 / total);

        // 差评原因分布（未填原因归入"未填写"）
        java.util.Map<String, Long> reasonMap = new java.util.LinkedHashMap<>();
        for (AnswerFeedback f : all) {
            if (f.getRating() != null && f.getRating() == 0) {
                String reason = (f.getReason() == null || f.getReason().isEmpty()) ? "未填写" : f.getReason();
                reasonMap.merge(reason, 1L, Long::sum);
            }
        }
        List<org.lexagent.model.vo.FeedbackStatisticsVO.ReasonItem> reasons = reasonMap.entrySet().stream()
                .map(e -> {
                    int percent = notHelpful == 0 ? 0
                            : (int) Math.round(e.getValue() * 100.0 / notHelpful);
                    return org.lexagent.model.vo.FeedbackStatisticsVO.ReasonItem.builder()
                            .reason(e.getKey())
                            .count(e.getValue())
                            .percent(percent)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(java.util.stream.Collectors.toList());

        // 近 14 天质量趋势（内存聚合，评价数据量小）
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.Map<String, List<AnswerFeedback>> byDate = new java.util.HashMap<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (AnswerFeedback f : all) {
            if (f.getCreatedAt() == null) {
                continue;
            }
            byDate.computeIfAbsent(
                    java.time.LocalDate.ofInstant(f.getCreatedAt().toInstant(),
                            java.time.ZoneId.systemDefault()).format(fmt),
                    k -> new ArrayList<>()).add(f);
        }
        List<org.lexagent.model.vo.FeedbackStatisticsVO.TrendItem> trend = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            String date = today.minusDays(i).format(fmt);
            List<AnswerFeedback> dayList = byDate.getOrDefault(date, List.of());
            long dayTotal = dayList.size();
            long dayHelpful = dayList.stream()
                    .filter(f -> f.getRating() != null && f.getRating() == 1).count();
            Integer dayGoodRate = dayTotal == 0 ? null
                    : (int) Math.round(dayHelpful * 100.0 / dayTotal);
            trend.add(org.lexagent.model.vo.FeedbackStatisticsVO.TrendItem.builder()
                    .date(date)
                    .total(dayTotal)
                    .goodRate(dayGoodRate)
                    .build());
        }

        return org.lexagent.model.vo.FeedbackStatisticsVO.builder()
                .totalCount(total)
                .helpfulCount(helpful)
                .notHelpfulCount(notHelpful)
                .goodRate(goodRate)
                .reasonDistribution(reasons)
                .trend(trend)
                .build();
    }

    private void checkRating(Integer rating) {
        if (rating == null || (rating != 0 && rating != 1)) {
            throw new BusinessException("评价结果不合法，仅支持 1(有帮助)/0(没有帮助)");
        }
    }

    /** 校验消息属于当前用户自己的会话（防越权评价他人回答） */
    private void checkMessageOwnership(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        Conversation conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException("无权评价该回答");
        }
        if (!AgentConstant.ROLE_ASSISTANT.equals(message.getRole())) {
            throw new BusinessException("仅可评价 AI 回答");
        }
    }
}
