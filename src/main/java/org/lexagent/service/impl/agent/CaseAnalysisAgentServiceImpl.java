package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.constants.PromptConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.service.agent.CaseAnalysisAgentService;
import org.lexagent.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Case Analysis Agent 实现
 * <p>
 * 调用 LLM（DeepSeek）将用户自然语言描述转换为结构化案件信息。
 * LLM 返回 JSON，使用 Hutool JSONUtil 解析为 CaseAnalysisResult。
 */
@Slf4j
@Service
public class CaseAnalysisAgentServiceImpl implements CaseAnalysisAgentService {

    @Autowired
    private LlmService llmService;

    @Override
    public CaseAnalysisResult analyze(String userQuery) {
        if (StrUtil.isBlank(userQuery)) {
            throw new BusinessException(AgentConstant.MESSAGE_CONTENT_EMPTY);
        }

        log.info("[CaseAnalysis] 开始分析用户问题，长度={}", userQuery.length());
        long start = System.currentTimeMillis();

        String userPrompt = String.format(PromptConstant.CASE_ANALYSIS_USER_TEMPLATE, userQuery);
        String llmResponse = llmService.chat(PromptConstant.CASE_ANALYSIS_SYSTEM, userPrompt);
        String json = llmService.extractJson(llmResponse);

        CaseAnalysisResult result;
        try {
            result = JSONUtil.toBean(json, CaseAnalysisResult.class);
        } catch (Exception e) {
            log.error("[CaseAnalysis] JSON 解析失败：{}", e.getMessage());
            log.error("[CaseAnalysis] LLM 原始回复：{}", llmResponse);
            // 解析失败时降级：返回基本结构
            result = CaseAnalysisResult.builder()
                    .domain("其他")
                    .caseType("待分类")
                    .facts(List.of(userQuery))
                    .disputePoints(new ArrayList<>())
                    .missingInformation(new ArrayList<>())
                    .build();
        }

        // 兜底：确保字段非 null
        if (result.getFacts() == null) result.setFacts(new ArrayList<>());
        if (result.getDisputePoints() == null) result.setDisputePoints(new ArrayList<>());
        if (result.getMissingInformation() == null) result.setMissingInformation(new ArrayList<>());

        long cost = System.currentTimeMillis() - start;
        log.info("[CaseAnalysis] 分析完成：domain={}, caseType={}, facts={}, 耗时={}ms",
                result.getDomain(), result.getCaseType(), result.getFacts().size(), cost);
        return result;
    }
}
