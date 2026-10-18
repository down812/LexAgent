package org.lexagent.service.impl;

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
    public Message save(String conversationId, String role, String content) {
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
    public List<ChatMessageVO> listByConversation(String conversationId) {
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));
        return list.stream().map(m -> ChatMessageVO.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build()).collect(Collectors.toList());
    }
}
