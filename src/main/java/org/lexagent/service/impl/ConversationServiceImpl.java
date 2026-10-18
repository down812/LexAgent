package org.lexagent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.exception.BusinessNotFoundException;
import org.lexagent.mapper.ConversationMapper;
import org.lexagent.model.entity.Conversation;
import org.lexagent.model.vo.ConversationVO;
import org.lexagent.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理服务实现
 * <p>
 * 会话ID由前端生成（UUID格式），后端不再自增。
 * 若前端未传 conversationId，后端生成 UUID 兜底。
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Override
    public Conversation getOrCreate(Long userId, String conversationId, String title) {
        // 前端传了 conversationId → 查是否已存在
        if (StrUtil.isNotBlank(conversationId)) {
            Conversation existing = conversationMapper.selectById(conversationId);
            if (existing != null) {
                // 校验归属
                if (!existing.getUserId().equals(userId)) {
                    throw new BusinessException("无权访问该会话");
                }
                return existing;
            }
            // 不存在 → 用前端提供的 ID 创建
        } else {
            // 前端未传 → 后端生成 UUID
            conversationId = IdUtil.simpleUUID();
        }

        // 创建新会话
        String convTitle = StrUtil.isNotBlank(title) ? title : "法律问诊";
        if (convTitle.length() > 50) {
            convTitle = convTitle.substring(0, 50);
        }
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(convTitle)
                .status(AgentConstant.CONVERSATION_ACTIVE)
                .build();
        conversationMapper.insert(conversation);
        log.info("创建新会话：id={}, userId={}, title={}", conversation.getId(), userId, convTitle);
        return conversation;
    }

    @Override
    public List<ConversationVO> listByUser(Long userId) {
        List<Conversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getStatus, AgentConstant.CONVERSATION_ACTIVE)
                        .orderByDesc(Conversation::getUpdatedAt));
        return list.stream().map(c -> ConversationVO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    public Conversation getByIdAndUser(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessNotFoundException(AgentConstant.CONVERSATION_NOT_EXIST);
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该会话");
        }
        return conversation;
    }

    @Override
    public void archive(String conversationId, Long userId) {
        Conversation conversation = getByIdAndUser(conversationId, userId);
        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .set(Conversation::getStatus, AgentConstant.CONVERSATION_ARCHIVED));
        log.info("会话已归档：id={}", conversationId);
    }
}
