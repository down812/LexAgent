package org.lexagent.service;

/**
 * LLM 调用封装服务
 * <p>
 * 基于 Spring AI ChatModel 调用 DeepSeek（兼容 OpenAI 协议）。
 * 统一封装：系统提示词 + 用户内容 → 文本结果。
 * 提供 JSON 提取辅助方法，便于各 Agent 解析结构化输出。
 */
public interface LlmService {

    /**
     * 调用 LLM（无系统提示词）
     *
     * @param userMessage 用户消息
     * @return LLM 回复文本
     */
    String chat(String userMessage);

    /**
     * 调用 LLM（带系统提示词）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage   用户消息
     * @return LLM 回复文本
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 从 LLM 回复中提取 JSON 字符串
     * <p>
     * 处理 LLM 可能包裹的 markdown 代码块（```json ... ```），
     * 以及前后多余的解释性文字。
     *
     * @param raw LLM 原始回复
     * @return 提取出的 JSON 字符串
     */
    String extractJson(String raw);

    /**
     * 检查 LLM 是否可用
     *
     * @return true 表示 ChatModel 已注入可用
     */
    boolean isAvailable();
}
