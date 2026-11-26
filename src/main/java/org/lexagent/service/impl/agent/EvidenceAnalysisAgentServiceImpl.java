package org.lexagent.service.impl.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.AgentConstant;
import org.lexagent.constants.PromptConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.model.vo.CaseContextVO;
import org.lexagent.model.vo.EvidenceAnalysisResult;
import org.lexagent.service.LlmService;
import org.lexagent.service.agent.EvidenceAnalysisAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evidence Analysis Agent 实现（V2.0 阶段五 / AGENT-001）
 *
 * 防幻觉约束与阶段三单证据分析一致：
 *   - 关键发现只能来自证据分析摘要
 *   - supportedFacts/missingEvidence 只能引用档案中已有的事实与证据名称
 *   - JSON 解析失败时降级为"仅总览"结果，不阻断问诊主流程
 */
@Slf4j
@Service
public class EvidenceAnalysisAgentServiceImpl implements EvidenceAnalysisAgentService {

    @Autowired
    private LlmService llmService;

    @Override
    public EvidenceAnalysisResult analyze(CaseContextVO context, String userQuery) {
        if (context == null) {
            throw new BusinessException("案件上下文为空，无法进行证据分析");
        }
        log.info("[EvidenceAnalysisAgent] 开始分析，caseId={}, query长度={}",
                context.getCaseId(), StrUtil.length(userQuery));
        long start = System.currentTimeMillis();

        String userPrompt = PromptConstant.EVIDENCE_AGENT_USER_TEMPLATE.formatted(
                context.toPromptText(), StrUtil.blankToDefault(userQuery, ""));

        EvidenceAnalysisResult result;
        String raw = llmService.chat(PromptConstant.EVIDENCE_AGENT_SYSTEM, userPrompt);
        String json = llmService.extractJson(raw);
        try {
            result = JSONUtil.toBean(json, EvidenceAnalysisResult.class);
        } catch (Exception e) {
            log.error("[EvidenceAnalysisAgent] JSON 解析失败：{}", e.getMessage());
            // 降级：仅以原始文本作为总览，不阻断流程
            result = EvidenceAnalysisResult.builder()
                    .evidenceOverview(StrUtil.blankToDefault(json, "证据分析结果解析失败"))
                    .keyFindings(new ArrayList<>())
                    .supportedFacts(new ArrayList<>())
                    .missingEvidence(new ArrayList<>())
                    .suggestions(new ArrayList<>())
                    .confidence(50)
                    .build();
        }

        // 兜底：确保集合字段非 null
        if (result.getKeyFindings() == null) result.setKeyFindings(new ArrayList<>());
        if (result.getSupportedFacts() == null) result.setSupportedFacts(new ArrayList<>());
        if (result.getMissingEvidence() == null) result.setMissingEvidence(new ArrayList<>());
        if (result.getSuggestions() == null) result.setSuggestions(new ArrayList<>());
        if (result.getConfidence() == null) result.setConfidence(60);

        long cost = System.currentTimeMillis() - start;
        log.info("[EvidenceAnalysisAgent] 分析完成：findings={}, missing={}, 耗时={}ms",
                result.getKeyFindings().size(), result.getMissingEvidence().size(), cost);
        return result;
    }
}
