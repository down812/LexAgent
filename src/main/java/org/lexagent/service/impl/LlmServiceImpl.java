package org.lexagent.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.service.LlmService;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM 调用封装实现
 * <p>
 * 基于 Spring AI ChatModel（由 spring-ai-starter-model-openai 自动配置，
 * 指向 DeepSeek 兼容的 OpenAI 协议端点）。
 */
@Slf4j
@Service
public class LlmServiceImpl implements LlmService {

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override
    public String chat(String userMessage) {
        return chat(null, userMessage);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new RuntimeException(AgentConstant.LLM_CALL_FAILED);
        }
        if (StrUtil.isBlank(userMessage)) {
            return "";
        }

        try {
            Prompt prompt;
            if (StrUtil.isNotBlank(systemPrompt)) {
                prompt = new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userMessage)
                ));
            } else {
                prompt = new Prompt(new UserMessage(userMessage));
            }

            ChatResponse response = chatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            log.debug("LLM 调用完成，回复长度={}", StrUtil.length(text));
            return text;
        } catch (Exception e) {
            log.error("LLM 调用异常：{}", e.getMessage(), e);
            throw new RuntimeException(AgentConstant.LLM_CALL_FAILED + "：" + e.getMessage(), e);
        }
    }

    @Override
    public String extractJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "{}";
        }
        String text = raw.trim();

        // 去除 markdown 代码块包裹
        if (text.contains("```")) {
            int start = text.indexOf("```");
            // 跳过 ```json 或 ``` 标记行
            int contentStart = text.indexOf('\n', start);
            if (contentStart > 0) {
                contentStart++;
            } else {
                contentStart = start + 3;
            }
            int end = text.indexOf("```", contentStart);
            if (end > contentStart) {
                text = text.substring(contentStart, end).trim();
            } else {
                text = text.substring(contentStart).trim();
            }
        }

        // 尝试提取首个 { 到最后一个 } 的范围
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }

        return text;
    }

    @Override
    public boolean isAvailable() {
        return chatModel != null;
    }
}
