package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.constants.PromptConstant;
import org.lexagent.model.vo.AgentEvent;
import org.lexagent.model.vo.CaseAnalysisResult;
import org.lexagent.model.vo.FinalLegalAnswer;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.model.vo.LegalAnalysisResult;
import org.lexagent.model.vo.ReviewResult;
import org.lexagent.service.agent.CaseAnalysisAgentService;
import org.lexagent.service.agent.LegalAnalysisAgentService;
import org.lexagent.service.agent.LegalSearchAgentService;
import org.lexagent.service.agent.ReviewerAgentService;
import org.lexagent.service.agent.SupervisorAgentService;
import org.lexagent.service.AgentTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Supervisor Agent 实现
 * <p>
 * 编排流程：
 *   1. Case Analysis Agent  —— 用户问题 → 结构化案件信息
 *   2. Legal Search Agent   —— 案件信息 → 检索法律条文
 *   3. Legal Analysis Agent  —— 案件+法律依据 → 法律分析
 *   4. Reviewer Agent        —— 法律分析 → 可信度审核
 *   5. 最终回答组装          —— 按需求文档第10章结构输出
 * <p>
 * 每个 Agent 执行时推送 SSE 事件，持久化 AgentTask 记录。
 */
@Slf4j
@Service
public class SupervisorAgentServiceImpl implements SupervisorAgentService {

    @Autowired
    private CaseAnalysisAgentService caseAnalysisAgent;

    @Autowired
    private LegalSearchAgentService legalSearchAgent;

    @Autowired
    private LegalAnalysisAgentService legalAnalysisAgent;

    @Autowired
    private ReviewerAgentService reviewerAgent;

    @Autowired
    private AgentTaskService agentTaskService;

    @Value("${lexagent.rag.agent.search-top-k:5}")
    private int searchTopK;

    @Override
    public FinalLegalAnswer execute(String userQuery, Long conversationId, Long messageId,
                                    Consumer<AgentEvent> eventCallback) {
        log.info("[Supervisor] 开始编排，conversationId={}, query长度={}", conversationId, StrUtil.length(userQuery));
        long totalStart = System.currentTimeMillis();

        // ========== 1. Case Analysis Agent ==========
        AgentTaskWrapper caseTask = startAgent(AgentConstant.AGENT_CASE_ANALYSIS,
                AgentConstant.AGENT_NAME_CASE_ANALYSIS, conversationId, messageId,
                userQuery, eventCallback);
        CaseAnalysisResult caseAnalysis;
        try {
            caseAnalysis = caseAnalysisAgent.analyze(userQuery);
        } catch (Exception e) {
            failAgent(caseTask, e.getMessage(), eventCallback);
            throw e;
        }
        String caseJson = JSONUtil.toJsonStr(caseAnalysis);
        successAgent(caseTask, caseJson, eventCallback,
                "案件分析完成：" + caseAnalysis.getDomain() + " · " + caseAnalysis.getCaseType());

        // ========== 2. Legal Search Agent ==========
        AgentTaskWrapper searchTask = startAgent(AgentConstant.AGENT_LEGAL_SEARCH,
                AgentConstant.AGENT_NAME_LEGAL_SEARCH, conversationId, messageId,
                caseJson, eventCallback);
        List<HybridSearchResult> searchResults;
        try {
            searchResults = legalSearchAgent.search(caseAnalysis, searchTopK);
        } catch (Exception e) {
            failAgent(searchTask, e.getMessage(), eventCallback);
            searchResults = new ArrayList<>();
        }
        String searchJson = JSONUtil.toJsonStr(searchResults);
        successAgent(searchTask, searchJson, eventCallback,
                "法律法规检索完成，命中 " + searchResults.size() + " 条");

        // 推送 citation 事件（法律依据列表）
        if (eventCallback != null && !searchResults.isEmpty()) {
            List<LegalAnalysisResult.LegalBasisItem> citations = searchResults.stream()
                    .map(r -> LegalAnalysisResult.LegalBasisItem.builder()
                            .lawName(r.getLawName())
                            .articleNo(r.getArticleNo())
                            .content(r.getContent())
                            .build())
                    .collect(Collectors.toList());
            eventCallback.accept(AgentEvent.of(AgentConstant.SSE_CITATION, null, null,
                    citations, "法律依据"));
        }

        // ========== 3. Legal Analysis Agent ==========
        AgentTaskWrapper analysisTask = startAgent(AgentConstant.AGENT_LEGAL_ANALYSIS,
                AgentConstant.AGENT_NAME_LEGAL_ANALYSIS, conversationId, messageId,
                caseJson + "|" + searchJson, eventCallback);
        LegalAnalysisResult legalAnalysis;
        try {
            legalAnalysis = legalAnalysisAgent.analyze(caseAnalysis, searchResults);
        } catch (Exception e) {
            failAgent(analysisTask, e.getMessage(), eventCallback);
            throw e;
        }
        String analysisJson = JSONUtil.toJsonStr(legalAnalysis);
        successAgent(analysisTask, analysisJson, eventCallback,
                "法律分析完成");

        // ========== 4. Reviewer Agent ==========
        AgentTaskWrapper reviewTask = startAgent(AgentConstant.AGENT_REVIEWER,
                AgentConstant.AGENT_NAME_REVIEWER, conversationId, messageId,
                analysisJson, eventCallback);
        ReviewResult reviewResult;
        try {
            reviewResult = reviewerAgent.review(caseAnalysis, legalAnalysis, searchResults);
        } catch (Exception e) {
            failAgent(reviewTask, e.getMessage(), eventCallback);
            // 审核失败不阻断流程，降级为默认通过
            reviewResult = ReviewResult.builder()
                    .passed(true)
                    .issues(new ArrayList<>())
                    .overallComment("审核服务异常，跳过审核")
                    .confidenceScore(60)
                    .build();
        }
        String reviewJson = JSONUtil.toJsonStr(reviewResult);
        successAgent(reviewTask, reviewJson, eventCallback,
                "法律依据核验完成，可信度：" + reviewResult.getConfidenceScore());

        // ========== 5. 最终回答组装 ==========
        FinalLegalAnswer finalAnswer = buildFinalAnswer(caseAnalysis, legalAnalysis, reviewResult);

        // 推送 content 事件（最终回答）
        if (eventCallback != null) {
            eventCallback.accept(AgentEvent.of(AgentConstant.SSE_CONTENT, null, null,
                    finalAnswer, "正在生成回答……"));
        }

        // 推送 done 事件
        if (eventCallback != null) {
            long totalCost = System.currentTimeMillis() - totalStart;
            eventCallback.accept(AgentEvent.of(AgentConstant.SSE_DONE, null, null,
                    null, "处理完成，耗时 " + totalCost + "ms"));
        }

        log.info("[Supervisor] 编排完成，总耗时={}ms，confidence={}",
                System.currentTimeMillis() - totalStart, reviewResult.getConfidenceScore());
        return finalAnswer;
    }

    // ==================== 最终回答组装 ====================

    /**
     * 按需求文档第10章结构组装最终回答：
     * 【初步判断】【法律依据】【结合你的情况】【处理建议】【风险提示】
     */
    private FinalLegalAnswer buildFinalAnswer(CaseAnalysisResult caseAnalysis,
                                              LegalAnalysisResult legalAnalysis,
                                              ReviewResult reviewResult) {
        // 初步判断
        String preliminary = buildPreliminaryJudgment(caseAnalysis, legalAnalysis);

        // 处理建议：若审核未通过且存在修正建议，追加审核意见
        List<String> suggestions = new ArrayList<>();
        if (legalAnalysis.getSuggestions() != null) {
            suggestions.addAll(legalAnalysis.getSuggestions());
        }
        if (reviewResult.getIssues() != null) {
            for (ReviewResult.ReviewIssue issue : reviewResult.getIssues()) {
                if ("HIGH".equals(issue.getSeverity()) && StrUtil.isNotBlank(issue.getSuggestion())) {
                    suggestions.add("【审核提示】" + issue.getSuggestion());
                }
            }
        }

        return FinalLegalAnswer.builder()
                .preliminaryJudgment(preliminary)
                .legalBasis(legalAnalysis.getLegalBasis())
                .caseAnalysis(legalAnalysis.getCaseAnalysis())
                .suggestions(suggestions)
                .riskWarning(PromptConstant.FINAL_ANSWER_RISK)
                .confidenceScore(reviewResult.getConfidenceScore())
                .reviewPassed(reviewResult.getPassed())
                .build();
    }

    /**
     * 生成初步判断文本
     */
    private String buildPreliminaryJudgment(CaseAnalysisResult caseAnalysis, LegalAnalysisResult analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append(PromptConstant.FINAL_ANSWER_INTRO).append("\n\n");
        sb.append("根据您的描述，您的情况属于").append(
                StrUtil.blankToDefault(caseAnalysis.getDomain(), "法律")).append("领域的");
        sb.append(StrUtil.blankToDefault(caseAnalysis.getCaseType(), "法律")).append("问题。\n\n");

        if (analysis.getLegalIssues() != null && !analysis.getLegalIssues().isEmpty()) {
            sb.append("涉及的主要法律问题：\n");
            for (int i = 0; i < analysis.getLegalIssues().size(); i++) {
                sb.append(i + 1).append(". ").append(analysis.getLegalIssues().get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== Agent 执行封装（含 SSE 推送 + 持久化） ====================

    private AgentTaskWrapper startAgent(String agentType, String agentName,
                                        Long conversationId, Long messageId,
                                        String input, Consumer<AgentEvent> callback) {
        var task = agentTaskService.create(conversationId, messageId, agentType, input);
        agentTaskService.markRunning(task.getId());
        if (callback != null) {
            callback.accept(AgentEvent.of(AgentConstant.SSE_AGENT_START,
                    agentType, agentName, null, agentName + " 开始执行"));
        }
        return new AgentTaskWrapper(task.getId(), agentType, agentName, System.currentTimeMillis());
    }

    private void successAgent(AgentTaskWrapper wrapper, String output,
                              Consumer<AgentEvent> callback, String message) {
        long cost = System.currentTimeMillis() - wrapper.startTime;
        agentTaskService.markSuccess(wrapper.taskId, output, cost);
        if (callback != null) {
            callback.accept(AgentEvent.of(AgentConstant.SSE_AGENT_RESULT,
                    wrapper.agentType, wrapper.agentName,
                    safeParseJson(output), message));
        }
    }

    private void failAgent(AgentTaskWrapper wrapper, String error,
                           Consumer<AgentEvent> callback) {
        long cost = System.currentTimeMillis() - wrapper.startTime;
        agentTaskService.markFailed(wrapper.taskId, error, cost);
        log.error("[Supervisor] Agent {} 执行失败：{}", wrapper.agentType, error);
        if (callback != null) {
            callback.accept(AgentEvent.of(AgentConstant.SSE_ERROR,
                    wrapper.agentType, wrapper.agentName, null,
                    wrapper.agentName + " 执行失败：" + error));
        }
    }

    /**
     * 安全解析 JSON（失败返回原始字符串）
     */
    private Object safeParseJson(String json) {
        if (StrUtil.isBlank(json)) return null;
        try {
            return JSONUtil.parse(json);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * AgentTask 执行上下文包装
     */
    private record AgentTaskWrapper(Long taskId, String agentType, String agentName, long startTime) {
    }
}
