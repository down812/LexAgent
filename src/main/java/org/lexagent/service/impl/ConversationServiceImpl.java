package org.lexagent.service.impl;

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
 * 数据库主键 id 自增；业务会话ID conversation_id 由前端生成（UUID）。
 * 所有按会话ID查询/校验均使用 conversation_id 字段。
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Override
    public Conversation getOrCreate(Long userId, String conversationId, String title) {
        // conversationId 由前端生成，必填（ChatRequestDTO 已校验 @NotBlank）
        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));
        if (existing != null) {
            // 校验归属
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException("无权访问该会话");
            }
            return existing;
        }

        // 不存在 → 用前端提供的 conversationId 创建新会话（id 自增）
        String convTitle = StrUtil.isNotBlank(title) ? title : "法律问诊";
        if (convTitle.length() > 50) {
            convTitle = convTitle.substring(0, 50);
        }
        Conversation conversation = Conversation.builder()
                .conversationId(conversationId)
                .userId(userId)
                .title(convTitle)
                .status(AgentConstant.CONVERSATION_ACTIVE)
                .build();
        conversationMapper.insert(conversation);
        log.info("创建新会话：id={}, conversationId={}, userId={}, title={}",
                conversation.getId(), conversationId, userId, convTitle);
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
                .conversationId(c.getConversationId())
                .title(c.getTitle())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    public Conversation getByIdAndUser(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId));
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
                        .eq(Conversation::getId, conversation.getId())
                        .set(Conversation::getStatus, AgentConstant.CONVERSATION_ARCHIVED));
        log.info("会话已归档：id={}, conversationId={}", conversation.getId(), conversationId);
    }
}
