package org.lexagent.service;

import org.lexagent.model.entity.Message;
import org.lexagent.model.vo.ChatMessageVO;

import java.util.List;

/**
 * 消息管理服务
 */
public interface MessageService {

    /**
     * 保存消息
     *
     * @param conversationId 会话ID（conversations.id 自增主键）
     * @param role           角色(user/assistant)
     * @param content        消息内容
     * @return 保存后的消息实体
     */
    Message save(Long conversationId, String role, String content);

    /**
     * 查询会话历史消息
     *
     * @param conversationId 会话ID（conversations.id 自增主键）
     * @return 消息 VO 列表（按时间升序）
     */
    List<ChatMessageVO> listByConversation(Long conversationId);
}
