package org.lexagent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.mapper.MessageMapper;
import org.lexagent.model.entity.Message;
import org.lexagent.model.vo.ChatMessageVO;
import org.lexagent.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息管理服务实现
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Override
    public Message save(Long conversationId, String role, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build();
        messageMapper.insert(message);
        log.info("消息已保存：conversationId={}, role={}, msgId={}", conversationId, role, message.getId());
        return message;
    }

    @Override
    public List<ChatMessageVO> listByConversation(Long conversationId) {
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));
        return list.stream().map(m -> {
            // AI 回答（assistant）的 content 存储的是 FinalLegalAnswer 的 JSON 字符串，
            // 这里解析为 JSON 对象返回，前端无需再 JSON.parse；
            // user 消息保持纯文本字符串。
            Object content = m.getContent();
            if (AgentConstant.ROLE_ASSISTANT.equals(m.getRole())
                    && StrUtil.isNotBlank(m.getContent())) {
                try {
                    content = JSONUtil.parseObj(m.getContent());
                } catch (Exception e) {
                    // 解析失败（如降级时存的纯文本）则保留原字符串
                    log.warn("assistant 消息 content 解析 JSON 失败，保留原字符串：msgId={}", m.getId());
                    content = m.getContent();
                }
            }
            return ChatMessageVO.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .content(content)
                    .createdAt(m.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
