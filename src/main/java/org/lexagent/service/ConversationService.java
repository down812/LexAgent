package org.lexagent.service;

import org.lexagent.model.entity.Conversation;
import org.lexagent.model.vo.ConversationVO;

import java.util.List;

/**
 * 会话管理服务
 */
public interface ConversationService {

    /**
     * 创建或获取会话（conversationId 为空时生成 UUID 创建新会话）
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（前端生成，为空则后端生成UUID）
     * @param title          会话标题
     * @return 会话实体
     */
    Conversation getOrCreate(Long userId, String conversationId, String title);

    /**
     * 查询用户会话列表
     *
     * @param userId 用户ID
     * @return 会话 VO 列表
     */
    List<ConversationVO> listByUser(Long userId);

    /**
     * 查询会话详情（校验归属）
     *
     * @param conversationId 会话ID
     * @param userId          用户ID（校验归属）
     * @return 会话实体
     */
    Conversation getByIdAndUser(String conversationId, Long userId);

    /**
     * 删除会话（软删除，标记为 ARCHIVED）
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     */
    void archive(String conversationId, Long userId);
}
