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
 * 编排流程（V2.0 阶段五升级，AGENT-002）：
 *   1. Case Analysis Agent        —— 用户问题（+案件档案上下文）→ 结构化案件信息
 *   2. Evidence Analysis Agent    —— 仅当有案件上下文且请求涉及证据材料时插入（按请求类型路由）
 *   3. Legal Search Agent         —— 案件信息 → 检索法律条文
 *   4. Legal Analysis Agent       —— 案件（含证据关键信息）+法律依据 → 法律分析
 *   5. Reviewer Agent             —— 法律分析 → 可信度审核（V2.0 含证据核验）
 *   6. 最终回答组装               —— 第10章结构 + CHAT-002 追问信息
 * <p>
 * 每个 Agent 执行时推送 SSE 事件，持久化 AgentTask 记录（V2.0 附带 case_id/task_group_id）。
 * 普通咨询（无案件上下文）完全保持 V1.0 流程，满足 CHAT-003 上下文隔离要求。
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
    private org.lexagent.service.agent.EvidenceAnalysisAgentService evidenceAnalysisAgent;

    @Autowired
    private AgentTaskService agentTaskService;

    @Value("${lexagent.rag.agent.search-top-k:5}")
    private int searchTopK;

    /**
     * 证据分析路由关键词（AGENT-002：根据用户请求类型判断是否调用 Evidence Analysis Agent）。
     * 采用规则路由：稳定可复现、零额外 LLM 延迟；后续可扩展为 LLM 意图分类。
     */
    private static final String[] EVIDENCE_ROUTE_KEYWORDS = {
            "证据", "材料", "清单", "缺少", "缺什么", "补充", "足够", "充分",
            "合同", "流水", "转账记录", "聊天记录", "截图", "录音", "考勤",
            "工资条", "发票", "票据", "证明文件", "能不能赢", "怎么收集"
    };

    @Override
    public FinalLegalAnswer execute(String userQuery, Long conversationId, Long messageId,
                                    Consumer<AgentEvent> eventCallback) {
        return execute(userQuery, conversationId, messageId, null, eventCallback);
    }

    @Override
    public FinalLegalAnswer execute(String userQuery, Long conversationId, Long messageId,
                                    org.lexagent.model.vo.CaseContextVO caseContext,
                                    Consumer<AgentEvent> eventCallback) {
        boolean withCase = caseContext != null;
        log.info("[Supervisor] 开始编排，conversationId={}, query长度={}, 案件上下文={}",
                conversationId, StrUtil.length(userQuery), withCase ? caseContext.getCaseNo() : "无");
        long totalStart = System.currentTimeMillis();
        // 一次完整协作任务标识（V2.0 12.9 agent_tasks 扩展）
        String taskGroupId = java.util.UUID.randomUUID().toString();
        Long caseId = withCase ? caseContext.getCaseId() : null;
        String contextText = withCase ? caseContext.toPromptText() : null;

        // ========== 1. Case Analysis Agent ==========
        AgentTaskWrapper caseTask = startAgent(AgentConstant.AGENT_CASE_ANALYSIS,
                AgentConstant.AGENT_NAME_CASE_ANALYSIS, conversationId, messageId, caseId, taskGroupId,
                userQuery, eventCallback);
        CaseAnalysisResult caseAnalysis;
        try {
            caseAnalysis = caseAnalysisAgent.analyze(userQuery, contextText);
        } catch (Exception e) {
            failAgent(caseTask, e.getMessage(), eventCallback);
            throw e;
        }
        String caseJson = JSONUtil.toJsonStr(caseAnalysis);
        successAgent(caseTask, caseJson, eventCallback,
                "案件分析完成：" + caseAnalysis.getDomain() + " · " + caseAnalysis.getCaseType());

        // ========== 2. Evidence Analysis Agent（按请求类型路由，AGENT-002 场景二） ==========
        org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult = null;
        if (withCase && needEvidenceAnalysis(userQuery, caseContext)) {
            AgentTaskWrapper evidenceTask = startAgent(AgentConstant.AGENT_EVIDENCE_ANALYSIS,
                    AgentConstant.AGENT_NAME_EVIDENCE_ANALYSIS, conversationId, messageId, caseId, taskGroupId,
                    userQuery, eventCallback);
            try {
                evidenceResult = evidenceAnalysisAgent.analyze(caseContext, userQuery);
            } catch (Exception e) {
                // 证据分析失败不阻断主流程（降级：跳过证据维度）
                failAgent(evidenceTask, e.getMessage(), eventCallback);
                evidenceResult = null;
            }
            if (evidenceResult != null) {
                String evidenceJson = JSONUtil.toJsonStr(evidenceResult);
                successAgent(evidenceTask, evidenceJson, eventCallback,
                        "证据分析完成，发现 " + evidenceResult.getMissingEvidence().size() + " 项缺失");
            }
        }

        // ========== 2.5 案件上下文合并（供 Legal Analysis / Reviewer 感知证据情况） ==========
        if (withCase) {
            mergeCaseContext(caseAnalysis, caseContext, evidenceResult);
            caseJson = JSONUtil.toJsonStr(caseAnalysis);
        }

        // ========== 3. Legal Search Agent ==========
        AgentTaskWrapper searchTask = startAgent(AgentConstant.AGENT_LEGAL_SEARCH,
                AgentConstant.AGENT_NAME_LEGAL_SEARCH, conversationId, messageId, caseId, taskGroupId,
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

        // ========== 4. Legal Analysis Agent ==========
        AgentTaskWrapper analysisTask = startAgent(AgentConstant.AGENT_LEGAL_ANALYSIS,
                AgentConstant.AGENT_NAME_LEGAL_ANALYSIS, conversationId, messageId, caseId, taskGroupId,
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

        // ========== 5. Reviewer Agent（V2.0 含证据核验） ==========
        AgentTaskWrapper reviewTask = startAgent(AgentConstant.AGENT_REVIEWER,
                AgentConstant.AGENT_NAME_REVIEWER, conversationId, messageId, caseId, taskGroupId,
                analysisJson, eventCallback);
        ReviewResult reviewResult;
        try {
            reviewResult = reviewerAgent.review(caseAnalysis, legalAnalysis, searchResults,
                    caseContext, evidenceResult);
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

        // ========== 6. 最终回答组装（含 CHAT-002 追问信息） ==========
        FinalLegalAnswer finalAnswer = buildFinalAnswer(caseAnalysis, legalAnalysis, reviewResult, evidenceResult);

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

        log.info("[Supervisor] 编排完成，总耗时={}ms，confidence={}, evidenceAgent={}",
                System.currentTimeMillis() - totalStart, reviewResult.getConfidenceScore(),
                evidenceResult != null);
        return finalAnswer;
    }

    /**
     * AGENT-002 路由判断：是否需要调用 Evidence Analysis Agent。
     * 规则：用户请求命中证据相关关键词，或案件已有证据/清单材料时，
     * 且用户问题偏向"证据收集/材料是否充分"类，才插入证据分析（避免普通咨询被拖慢）。
     */
    private boolean needEvidenceAnalysis(String userQuery, org.lexagent.model.vo.CaseContextVO context) {
        String q = StrUtil.blankToDefault(userQuery, "");
        for (String kw : EVIDENCE_ROUTE_KEYWORDS) {
            if (q.contains(kw)) {
                log.info("[Supervisor] 命中证据路由关键词[{}]，插入 Evidence Analysis Agent", kw);
                return true;
            }
        }
        // 案件已有证据且用户在追问案情时，也可以提供证据视角（宽松触发）
        return !context.getEvidences().isEmpty() && (q.contains("分析") || q.contains("怎么办")
                || q.contains("建议") || q.contains("评估"));
    }

    /**
     * 将案件档案事实、证据概览与证据分析关键信息合并进 CaseAnalysisResult，
     * 使 Legal Analysis Agent 无需改接口即可感知案件上下文（facts 标注来源防混淆）。
     */
    private void mergeCaseContext(CaseAnalysisResult caseAnalysis,
                                  org.lexagent.model.vo.CaseContextVO context,
                                  org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult) {
        if (caseAnalysis.getFacts() == null) {
            caseAnalysis.setFacts(new ArrayList<>());
        }
        // 已确认案件事实（避免与本次描述重复）
        for (String fact : context.getConfirmedFacts()) {
            if (!caseAnalysis.getFacts().contains(fact)) {
                caseAnalysis.getFacts().add(fact);
            }
        }
        // 证据概览
        for (org.lexagent.model.vo.CaseContextVO.EvidenceBrief ev : context.getEvidences()) {
            StringBuilder line = new StringBuilder("【已有证据】").append(ev.getEvidenceName());
            if (ev.getEvidenceType() != null && !ev.getEvidenceType().isEmpty()) {
                line.append("（类型：").append(ev.getEvidenceType());
                if (ev.getAnalysisSummary() != null && !ev.getAnalysisSummary().isEmpty()) {
                    line.append("；摘要：").append(StrUtil.sub(ev.getAnalysisSummary(), 0, 80));
                }
                line.append("）");
            }
            caseAnalysis.getFacts().add(line.toString());
        }
        // 证据分析关键发现
        if (evidenceResult != null) {
            for (String finding : evidenceResult.getKeyFindings()) {
                caseAnalysis.getFacts().add("【证据分析】" + finding);
            }
        }
    }

    // ==================== 最终回答组装 ====================

    /**
     * 按需求文档第10章结构组装最终回答：
     * 【初步判断】【法律依据】【结合你的情况】【处理建议】【风险提示】
     * V2.0（CHAT-002）：附加缺失关键信息追问（missingInfo），用户可一键补充为案件事实
     */
    private FinalLegalAnswer buildFinalAnswer(CaseAnalysisResult caseAnalysis,
                                              LegalAnalysisResult legalAnalysis,
                                              ReviewResult reviewResult,
                                              org.lexagent.model.vo.EvidenceAnalysisResult evidenceResult) {
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

        // CHAT-002 追问信息：案件分析缺失信息 + 证据缺失情况（去重、限 5 条）
        List<String> missingInfo = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (caseAnalysis.getMissingInformation() != null) {
            for (String m : caseAnalysis.getMissingInformation()) {
                if (StrUtil.isNotBlank(m) && seen.add(m.trim())) {
                    missingInfo.add(m.trim());
                }
            }
        }
        if (evidenceResult != null) {
            for (String m : evidenceResult.getMissingEvidence()) {
                if (StrUtil.isNotBlank(m) && seen.add(m.trim())) {
                    missingInfo.add(m.trim());
                }
            }
        }
        if (missingInfo.size() > 5) {
            missingInfo = new ArrayList<>(missingInfo.subList(0, 5));
        }

        return FinalLegalAnswer.builder()
                .preliminaryJudgment(preliminary)
                .legalBasis(legalAnalysis.getLegalBasis())
                .caseAnalysis(legalAnalysis.getCaseAnalysis())
                .suggestions(suggestions)
                .riskWarning(PromptConstant.FINAL_ANSWER_RISK)
                .confidenceScore(reviewResult.getConfidenceScore())
                .reviewPassed(reviewResult.getPassed())
                .missingInfo(missingInfo.isEmpty() ? null : missingInfo)
                .evidenceReferences(evidenceResult == null
                        || evidenceResult.getKeyFindings().isEmpty()
                        ? null : evidenceResult.getKeyFindings())
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
        return startAgent(agentType, agentName, conversationId, messageId, null, null, input, callback);
    }

    private AgentTaskWrapper startAgent(String agentType, String agentName,
                                        Long conversationId, Long messageId,
                                        Long caseId, String taskGroupId,
                                        String input, Consumer<AgentEvent> callback) {
        var task = agentTaskService.create(conversationId, messageId, caseId, taskGroupId, agentType, input);
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
