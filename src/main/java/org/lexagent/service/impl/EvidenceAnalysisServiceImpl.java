package org.lexagent.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.CaseConstant;
import org.lexagent.constants.PromptConstant;
import org.lexagent.constants.RagConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.mapper.CaseEvidenceMapper;
import org.lexagent.mapper.CaseFactMapper;
import org.lexagent.mapper.EvidenceAnalysisMapper;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.model.entity.CaseFact;
import org.lexagent.model.entity.EvidenceAnalysis;
import org.lexagent.model.vo.EvidenceAnalysisVO;
import org.lexagent.model.vo.EvidenceCategoryRecommendVO;
import org.lexagent.model.vo.EvidenceIntegrityVO;
import org.lexagent.model.vo.EvidenceKeyInfoVO;
import org.lexagent.model.vo.EvidenceRelatedFactVO;
import org.lexagent.model.vo.EvidenceUncertainVO;
import org.lexagent.service.AgentTaskService;
import org.lexagent.service.CaseService;
import org.lexagent.service.EvidenceAnalysisService;
import org.lexagent.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 证据智能分析服务实现（V2.0 阶段三）
 *
 * 分析流程（EVIDENCE-006/007/008 一次 LLM 调用完成）：
 *   校验证据与识别文本 → 置 analysis_status=PROCESSING →
 *   组装提示词（案件类型 + 文件名 + 识别文本（优先用户修正文本）+ 案件事实）→ LLM →
 *   解析结构化 JSON（factId 必须与真实案件事实匹配，防幻觉）→
 *   落库 evidence_analysis → SUCCESS
 *
 * 防幻觉机制：
 *   1. 提示词硬性约束：提取内容只能来自识别文本原文
 *   2. 程序校验：LLM 返回的 relatedFacts.factId 必须存在于真实案件事实中，否则丢弃
 *   3. relatedFacts 的 factContent 以数据库事实为准，不采信 LLM 输出的事实内容
 */
@Slf4j
@Service
public class EvidenceAnalysisServiceImpl implements EvidenceAnalysisService {

    /** 传给 LLM 的识别文本上限（避免超长上下文） */
    private static final int OCR_TEXT_MAX_LENGTH = 6000;

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private CaseFactMapper caseFactMapper;

    @Autowired
    private EvidenceAnalysisMapper evidenceAnalysisMapper;

    @Autowired
    private org.lexagent.mapper.LegalCaseMapper legalCaseMapper;

    @Autowired
    private CaseService caseService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private AgentTaskService agentTaskService;

    @Async("ragProcessExecutor")
    @Override
    public void analyzeAsync(Long evidenceId) {
        log.info("证据智能分析异步任务开始：evidenceId={}", evidenceId);
        try {
            analyze(evidenceId);
        } catch (Exception e) {
            // analyze 内部已捕获并更新状态，这里兜底防止异常逃逸
            log.error("证据智能分析异步任务异常逃逸：evidenceId={}", evidenceId, e);
        }
    }

    @Override
    public EvidenceAnalysisVO analyze(Long evidenceId) {
        CaseEvidence evidence = caseEvidenceMapper.selectById(evidenceId);
        if (evidence == null) {
            throw new BusinessException(CaseConstant.EVIDENCE_NOT_EXIST);
        }
        // 识别文本优先使用用户修正文本，其次原始识别文本
        String text = (evidence.getCorrectedText() != null && !evidence.getCorrectedText().isEmpty())
                ? evidence.getCorrectedText() : evidence.getOcrText();
        if (text == null || text.isEmpty()
                || !RagConstant.FILE_STATUS_SUCCESS.equals(evidence.getOcrStatus())) {
            throw new BusinessException(CaseConstant.EVIDENCE_OCR_NOT_READY);
        }

        // 置为分析中（供前端轮询）
        evidence.setAnalysisStatus(CaseConstant.ANALYSIS_STATUS_PROCESSING);
        caseEvidenceMapper.updateById(evidence);

        // Agent 执行记录（conversationId/messageId 阶段五接入问诊链路后再关联）
        org.lexagent.model.entity.AgentTask agentTask = agentTaskService.create(
                null, null, CaseConstant.AGENT_TYPE_EVIDENCE_ANALYSIS,
                buildInputSnapshot(evidence, text));
        agentTaskService.markRunning(agentTask.getId());
        long start = System.currentTimeMillis();

        try {
            if (!llmService.isAvailable()) {
                throw new BusinessException(CaseConstant.EVIDENCE_ANALYSIS_LLM_UNAVAILABLE);
            }

            // 案件事实列表与案件类型（LLM 输入需要案件类型上下文）
            List<CaseFact> facts = caseFactMapper.selectList(
                    new LambdaQueryWrapper<CaseFact>()
                            .eq(CaseFact::getCaseId, evidence.getCaseId())
                            .orderByAsc(CaseFact::getCreatedAt));
            org.lexagent.model.entity.LegalCase legalCase = legalCaseMapper.selectById(evidence.getCaseId());
            String caseType = legalCase == null ? "" : nullToEmpty(legalCase.getCaseType());

            // 组装提示词并调用 LLM
            String userPrompt = PromptConstant.EVIDENCE_ANALYSIS_USER_TEMPLATE.formatted(
                    caseType,
                    nullToEmpty(evidence.getEvidenceName()),
                    truncate(text),
                    buildFactListText(facts));
            String raw = llmService.chat(PromptConstant.EVIDENCE_ANALYSIS_SYSTEM, userPrompt);
            String json = llmService.extractJson(raw);
            if (json == null || json.isEmpty()) {
                throw new BusinessException("LLM 未返回有效 JSON，无法解析分析结果");
            }
            JSONObject obj = JSONUtil.parseObj(json);

            // 解析并落库（factId 程序校验，防幻觉）
            EvidenceAnalysis record = buildAnalysisRecord(evidence, facts, obj);
            evidenceAnalysisMapper.insert(record);

            evidence.setAnalysisStatus(CaseConstant.ANALYSIS_STATUS_SUCCESS);
            caseEvidenceMapper.updateById(evidence);

            long duration = System.currentTimeMillis() - start;
            agentTaskService.markSuccess(agentTask.getId(), JSONUtil.toJsonStr(record), duration);
            log.info("证据[{}]智能分析完成，耗时 {} ms，置信度 {}", evidenceId, duration, record.getConfidence());
            return toVO(record);
        } catch (Exception e) {
            log.error("证据[{}]智能分析失败", evidenceId, e);
            CaseEvidence latest = caseEvidenceMapper.selectById(evidenceId);
            if (latest != null) {
                latest.setAnalysisStatus(CaseConstant.ANALYSIS_STATUS_FAILED);
                caseEvidenceMapper.updateById(latest);
            }
            agentTaskService.markFailed(agentTask.getId(), e.getMessage(), System.currentTimeMillis() - start);
            throw (e instanceof BusinessException be) ? be
                    : new BusinessException("证据智能分析失败: " + e.getMessage());
        }
    }

    @Override
    public EvidenceAnalysisVO getAnalysis(Long evidenceId) {
        EvidenceAnalysis latest = evidenceAnalysisMapper.selectOne(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getEvidenceId, evidenceId)
                        .orderByDesc(EvidenceAnalysis::getCreatedAt)
                        .last("LIMIT 1"));
        return latest == null ? null : toVO(latest);
    }

    @Override
    public EvidenceAnalysisVO updateRelatedFact(Long evidenceId, Long factId, boolean confirmed) {
        EvidenceAnalysis latest = evidenceAnalysisMapper.selectOne(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getEvidenceId, evidenceId)
                        .orderByDesc(EvidenceAnalysis::getCreatedAt)
                        .last("LIMIT 1"));
        if (latest == null) {
            throw new BusinessException(CaseConstant.EVIDENCE_ANALYSIS_NOT_EXIST);
        }
        List<EvidenceRelatedFactVO> related = parseRelatedFacts(latest.getRelatedFacts());
        boolean found = false;
        for (EvidenceRelatedFactVO item : related) {
            if (item.getFactId() != null && item.getFactId().equals(factId)) {
                item.setConfirmed(confirmed);
                found = true;
            }
        }
        if (!found) {
            throw new BusinessException("该事实不在本证据的关联结果中");
        }
        latest.setRelatedFacts(JSONUtil.toJsonStr(related));
        evidenceAnalysisMapper.updateById(latest);
        return toVO(latest);
    }

    @Override
    public EvidenceIntegrityVO analyzeIntegrity(Long caseId) {
        // 数据权限：属主或管理员
        org.lexagent.model.entity.LegalCase legalCase = caseService.checkCasePermission(caseId);
        if (!llmService.isAvailable()) {
            throw new BusinessException(CaseConstant.INTEGRITY_LLM_UNAVAILABLE);
        }

        List<CaseFact> facts = caseFactMapper.selectList(
                new LambdaQueryWrapper<CaseFact>()
                        .eq(CaseFact::getCaseId, caseId)
                        .orderByAsc(CaseFact::getCreatedAt));
        List<CaseEvidence> evidences = caseEvidenceMapper.selectList(
                new LambdaQueryWrapper<CaseEvidence>()
                        .eq(CaseEvidence::getCaseId, caseId)
                        .orderByAsc(CaseEvidence::getCreatedAt));

        String userPrompt = PromptConstant.EVIDENCE_INTEGRITY_USER_TEMPLATE.formatted(
                nullToEmpty(legalCase.getCaseType()),
                truncate(nullToEmpty(legalCase.getDescription())),
                buildFactListText(facts),
                buildEvidenceListText(evidences));

        String raw = llmService.chat(PromptConstant.EVIDENCE_INTEGRITY_SYSTEM, userPrompt);
        String json = llmService.extractJson(raw);
        if (json == null || json.isEmpty()) {
            throw new BusinessException("LLM 未返回有效 JSON，无法解析完整性分析结果");
        }
        return parseIntegrity(json);
    }

    // ===================== 内部工具 =====================

    private String buildInputSnapshot(CaseEvidence evidence, String text) {
        JSONObject input = new JSONObject();
        input.set("evidenceId", evidence.getId());
        input.set("evidenceName", evidence.getEvidenceName());
        input.set("fileType", evidence.getFileType());
        input.set("ocrTextLength", text.length());
        return input.toString();
    }

    private EvidenceAnalysis buildAnalysisRecord(CaseEvidence evidence, List<CaseFact> facts, JSONObject obj) {
        // —— 分类推荐 ——
        EvidenceCategoryRecommendVO category = new EvidenceCategoryRecommendVO();
        JSONObject catObj = obj.getJSONObject("categoryRecommendation");
        if (catObj != null) {
            category.setCategory(catObj.getStr("category"));
            Double conf = catObj.getDouble("confidence");
            category.setConfidence(conf);
        }

        // —— 关键信息 ——
        List<EvidenceKeyInfoVO> keyInfos = new ArrayList<>();
        JSONArray keyArr = obj.getJSONArray("keyInformation");
        if (keyArr != null) {
            for (Object o : keyArr) {
                JSONObject jo = (JSONObject) o;
                EvidenceKeyInfoVO vo = new EvidenceKeyInfoVO();
                vo.setField(jo.getStr("field"));
                vo.setValue(jo.getStr("value"));
                vo.setSourceText(jo.getStr("sourceText"));
                keyInfos.add(vo);
            }
        }

        // —— 不确定信息 ——
        List<EvidenceUncertainVO> uncertains = new ArrayList<>();
        JSONArray uArr = obj.getJSONArray("uncertainInformation");
        if (uArr != null) {
            for (Object o : uArr) {
                JSONObject jo = (JSONObject) o;
                EvidenceUncertainVO vo = new EvidenceUncertainVO();
                vo.setField(jo.getStr("field"));
                vo.setReason(jo.getStr("reason"));
                uncertains.add(vo);
            }
        }

        // —— 主体 ——
        List<String> parties = new ArrayList<>();
        JSONArray pArr = obj.getJSONArray("parties");
        if (pArr != null) {
            for (Object o : pArr) {
                parties.add(String.valueOf(o));
            }
        }

        // —— 关联事实：factId 必须与真实案件事实匹配（防幻觉），factContent 以数据库为准 ——
        Map<Long, CaseFact> factMap = facts.stream()
                .collect(Collectors.toMap(CaseFact::getId, f -> f, (a, b) -> a));
        List<EvidenceRelatedFactVO> related = new ArrayList<>();
        JSONArray rArr = obj.getJSONArray("relatedFacts");
        if (rArr != null) {
            for (Object o : rArr) {
                JSONObject jo = (JSONObject) o;
                Long factId = jo.getLong("factId");
                CaseFact fact = factId == null ? null : factMap.get(factId);
                if (fact == null) {
                    log.warn("丢弃 LLM 返回的无效事实关联：evidenceId={}, factId={}", evidence.getId(), factId);
                    continue;
                }
                EvidenceRelatedFactVO vo = new EvidenceRelatedFactVO();
                vo.setFactId(fact.getId());
                vo.setFactContent(fact.getFactContent());
                vo.setReason(jo.getStr("reason"));
                vo.setConfirmed(false); // 默认未确认，由用户决定
                related.add(vo);
            }
        }

        // —— 组装存储 JSON ——
        JSONObject extracted = new JSONObject();
        extracted.set("categoryRecommendation", category);
        extracted.set("parties", parties);
        extracted.set("keyInformation", keyInfos);
        extracted.set("uncertainInformation", uncertains);

        BigDecimal confidence = null;
        Integer conf = obj.getInt("confidence");
        if (conf != null) {
            confidence = BigDecimal.valueOf(Math.min(100, Math.max(0, conf)));
        }

        return EvidenceAnalysis.builder()
                .evidenceId(evidence.getId())
                .evidenceType(obj.getStr("evidenceType"))
                .extractedData(extracted.toString())
                .relatedFacts(JSONUtil.toJsonStr(related))
                .analysisResult(obj.getStr("summary"))
                .confidence(confidence)
                .createdAt(new java.util.Date())
                .build();
    }

    private EvidenceIntegrityVO parseIntegrity(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        EvidenceIntegrityVO vo = new EvidenceIntegrityVO();

        List<EvidenceIntegrityVO.CoveredFact> covered = new ArrayList<>();
        JSONArray cArr = obj.getJSONArray("coveredFacts");
        if (cArr != null) {
            for (Object o : cArr) {
                JSONObject jo = (JSONObject) o;
                EvidenceIntegrityVO.CoveredFact cf = new EvidenceIntegrityVO.CoveredFact();
                cf.setFact(jo.getStr("fact"));
                List<String> evs = new ArrayList<>();
                JSONArray evArr = jo.getJSONArray("evidences");
                if (evArr != null) {
                    for (Object e : evArr) {
                        evs.add(String.valueOf(e));
                    }
                }
                cf.setEvidences(evs);
                covered.add(cf);
            }
        }
        vo.setCoveredFacts(covered);
        vo.setUncoveredFacts(toStringList(obj.getJSONArray("uncoveredFacts")));
        vo.setToVerify(toStringList(obj.getJSONArray("toVerify")));

        List<EvidenceIntegrityVO.SuggestedMaterial> materials = new ArrayList<>();
        JSONArray mArr = obj.getJSONArray("suggestedMaterials");
        if (mArr != null) {
            for (Object o : mArr) {
                JSONObject jo = (JSONObject) o;
                EvidenceIntegrityVO.SuggestedMaterial sm = new EvidenceIntegrityVO.SuggestedMaterial();
                sm.setMaterial(jo.getStr("material"));
                sm.setPurpose(jo.getStr("purpose"));
                sm.setPriority(jo.getStr("priority"));
                materials.add(sm);
            }
        }
        vo.setSuggestedMaterials(materials);
        vo.setSummary(obj.getStr("summary"));
        return vo;
    }

    private List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (Object o : arr) {
                list.add(String.valueOf(o));
            }
        }
        return list;
    }

    private List<EvidenceRelatedFactVO> parseRelatedFacts(String json) {
        List<EvidenceRelatedFactVO> list = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return list;
        }
        JSONArray arr = JSONUtil.parseArray(json);
        for (Object o : arr) {
            JSONObject jo = (JSONObject) o;
            EvidenceRelatedFactVO vo = new EvidenceRelatedFactVO();
            vo.setFactId(jo.getLong("factId"));
            vo.setFactContent(jo.getStr("factContent"));
            vo.setReason(jo.getStr("reason"));
            vo.setConfirmed(Boolean.TRUE.equals(jo.getBool("confirmed")));
            list.add(vo);
        }
        return list;
    }

    private EvidenceAnalysisVO toVO(EvidenceAnalysis record) {
        EvidenceAnalysisVO vo = new EvidenceAnalysisVO();
        vo.setId(record.getId());
        vo.setEvidenceId(record.getEvidenceId());
        vo.setEvidenceType(record.getEvidenceType());
        vo.setAnalysisResult(record.getAnalysisResult());
        vo.setConfidence(record.getConfidence());
        vo.setCreatedAt(record.getCreatedAt());

        if (record.getExtractedData() != null && !record.getExtractedData().isEmpty()) {
            JSONObject extracted = JSONUtil.parseObj(record.getExtractedData());
            JSONObject catObj = extracted.getJSONObject("categoryRecommendation");
            if (catObj != null) {
                EvidenceCategoryRecommendVO cat = new EvidenceCategoryRecommendVO();
                cat.setCategory(catObj.getStr("category"));
                cat.setConfidence(catObj.getDouble("confidence"));
                vo.setCategoryRecommendation(cat);
            }
            vo.setParties(toStringList(extracted.getJSONArray("parties")));

            List<EvidenceKeyInfoVO> keyInfos = new ArrayList<>();
            JSONArray keyArr = extracted.getJSONArray("keyInformation");
            if (keyArr != null) {
                for (Object o : keyArr) {
                    JSONObject jo = (JSONObject) o;
                    EvidenceKeyInfoVO k = new EvidenceKeyInfoVO();
                    k.setField(jo.getStr("field"));
                    k.setValue(jo.getStr("value"));
                    k.setSourceText(jo.getStr("sourceText"));
                    keyInfos.add(k);
                }
            }
            vo.setKeyInformation(keyInfos);

            List<EvidenceUncertainVO> uncertains = new ArrayList<>();
            JSONArray uArr = extracted.getJSONArray("uncertainInformation");
            if (uArr != null) {
                for (Object o : uArr) {
                    JSONObject jo = (JSONObject) o;
                    EvidenceUncertainVO u = new EvidenceUncertainVO();
                    u.setField(jo.getStr("field"));
                    u.setReason(jo.getStr("reason"));
                    uncertains.add(u);
                }
            }
            vo.setUncertainInformation(uncertains);
        }

        vo.setRelatedFacts(parseRelatedFacts(record.getRelatedFacts()));
        return vo;
    }

    private String buildFactListText(List<CaseFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return "（暂无案件事实）";
        }
        StringBuilder sb = new StringBuilder();
        for (CaseFact fact : facts) {
            sb.append("factId=").append(fact.getId())
                    .append("，内容：").append(truncate(fact.getFactContent()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildEvidenceListText(List<CaseEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "（暂无已上传证据）";
        }
        StringBuilder sb = new StringBuilder();
        for (CaseEvidence ev : evidences) {
            sb.append("- 名称：").append(ev.getEvidenceName())
                    .append("，分类：").append(nullToEmpty(ev.getCategory()))
                    .append("，OCR状态：").append(nullToEmpty(ev.getOcrStatus()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > OCR_TEXT_MAX_LENGTH
                ? text.substring(0, OCR_TEXT_MAX_LENGTH) + "…（文本过长已截断）"
                : text;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
