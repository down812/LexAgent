package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
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
import org.lexagent.mapper.EvidenceChecklistMapper;
import org.lexagent.mapper.EvidenceChecklistRelationMapper;
import org.lexagent.model.dto.ChecklistCreateDTO;
import org.lexagent.model.dto.ChecklistUpdateDTO;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.model.entity.CaseFact;
import org.lexagent.model.entity.EvidenceAnalysis;
import org.lexagent.model.entity.EvidenceChecklist;
import org.lexagent.model.entity.EvidenceChecklistRelation;
import org.lexagent.model.entity.LegalCase;
import org.lexagent.model.vo.EvidenceChecklistVO;
import org.lexagent.model.vo.HybridSearchResult;
import org.lexagent.result.Result;
import org.lexagent.service.CaseService;
import org.lexagent.service.EvidenceChecklistService;
import org.lexagent.service.HybridSearchService;
import org.lexagent.service.LlmService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 证据清单服务实现（V2.0 阶段四）
 *
 * 生成流程（LIST-001）：
 *   权限校验 → 事实非空校验（无事实无法针对性生成）→
 *   组装输入（案件类型/描述/事实/已上传证据/证据分析摘要/法律检索参考）→ LLM →
 *   解析 items（status 校验限定 REQUIRED/OPTIONAL）→
 *   同名项目跳过（保留用户已编辑/已关联的项目）→ 插入新项目 → 返回清单 + summary
 *
 * 关联机制（LIST-002）：
 *   关联实际证据后 REQUIRED → UPLOADED；取消关联后若无其他关联，UPLOADED → REQUIRED。
 *   推荐关联：基于清单名称与证据名称/分析类型的字符重叠度做轻量匹配（不调 LLM，稳定可复现），
 *   仅作为建议展示，由用户确认。
 */
@Slf4j
@Service
public class EvidenceChecklistServiceImpl implements EvidenceChecklistService {

    private static final Set<String> VALID_STATUSES = Set.of(
            CaseConstant.CHECKLIST_STATUS_REQUIRED,
            CaseConstant.CHECKLIST_STATUS_UPLOADED,
            CaseConstant.CHECKLIST_STATUS_CONFIRMED,
            CaseConstant.CHECKLIST_STATUS_OPTIONAL);

    private static final Set<String> GENERATE_STATUSES = Set.of(
            CaseConstant.CHECKLIST_STATUS_REQUIRED,
            CaseConstant.CHECKLIST_STATUS_OPTIONAL);

    private static final int RAG_REFERENCE_COUNT = 5;
    private static final double SUGGESTION_THRESHOLD = 0.5;

    @Autowired
    private EvidenceChecklistMapper evidenceChecklistMapper;

    @Autowired
    private EvidenceChecklistRelationMapper relationMapper;

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private CaseFactMapper caseFactMapper;

    @Autowired
    private EvidenceAnalysisMapper evidenceAnalysisMapper;

    @Autowired
    private CaseService caseService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private HybridSearchService hybridSearchService;

    // ===================== LIST-001 生成 =====================

    @Override
    public Result<List<EvidenceChecklistVO>> generate(Long caseId) {
        LegalCase legalCase = caseService.checkCasePermission(caseId);
        if (!llmService.isAvailable()) {
            throw new BusinessException(CaseConstant.CHECKLIST_LLM_UNAVAILABLE);
        }
        List<CaseFact> facts = caseFactMapper.selectList(
                new LambdaQueryWrapper<CaseFact>()
                        .eq(CaseFact::getCaseId, caseId)
                        .orderByAsc(CaseFact::getCreatedAt));
        if (facts.isEmpty()) {
            throw new BusinessException(CaseConstant.CHECKLIST_FACTS_EMPTY);
        }
        List<CaseEvidence> evidences = caseEvidenceMapper.selectList(
                new LambdaQueryWrapper<CaseEvidence>()
                        .eq(CaseEvidence::getCaseId, caseId)
                        .orderByAsc(CaseEvidence::getCreatedAt));

        String userPrompt = PromptConstant.CHECKLIST_GENERATE_USER_TEMPLATE.formatted(
                nullToEmpty(legalCase.getCaseType()),
                truncate(nullToEmpty(legalCase.getDescription())),
                buildFactText(facts),
                buildEvidenceText(evidences),
                buildAnalysisSummary(evidences),
                buildRagReference(legalCase, facts));

        String raw = llmService.chat(PromptConstant.CHECKLIST_GENERATE_SYSTEM, userPrompt);
        String json = llmService.extractJson(raw);
        if (json == null || json.isEmpty()) {
            throw new BusinessException("LLM 未返回有效 JSON，无法生成证据清单");
        }
        JSONObject obj = JSONUtil.parseObj(json);
        JSONArray items = obj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException("LLM 未生成任何清单项目");
        }

        // 同名项目跳过：保留用户已编辑/已关联的项目（重新生成不清空已有成果）
        Set<String> existingNames = evidenceChecklistMapper.selectList(
                        new LambdaQueryWrapper<EvidenceChecklist>().eq(EvidenceChecklist::getCaseId, caseId))
                .stream()
                .map(EvidenceChecklist::getEvidenceName)
                .collect(Collectors.toSet());

        int inserted = 0;
        for (Object o : items) {
            JSONObject jo = (JSONObject) o;
            String name = jo.getStr("evidenceName");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            name = name.trim();
            if (existingNames.contains(name)) {
                continue;
            }
            String status = jo.getStr("status");
            if (status == null || !GENERATE_STATUSES.contains(status)) {
                status = CaseConstant.CHECKLIST_STATUS_REQUIRED; // 非法状态兜底
            }
            EvidenceChecklist item = EvidenceChecklist.builder()
                    .caseId(caseId)
                    .evidenceName(name)
                    .purpose(truncate(jo.getStr("purpose"), 500))
                    .status(status)
                    .relatedFact(truncate(jo.getStr("relatedFact"), 500))
                    .createdAt(DateTime.now())
                    .updatedAt(DateTime.now())
                    .build();
            evidenceChecklistMapper.insert(item);
            existingNames.add(name);
            inserted++;
        }
        log.info("案件[{}]证据清单生成完成：LLM 输出 {} 项，实际新增 {} 项（同名跳过）",
                caseId, items.size(), inserted);

        String summary = obj.getStr("summary");
        return Result.success(summary == null || summary.isEmpty()
                ? "证据清单已生成" : summary, getByCase(caseId));
    }

    // ===================== 查询 / CRUD =====================

    @Override
    public List<EvidenceChecklistVO> getByCase(Long caseId) {
        caseService.checkCasePermission(caseId);
        List<EvidenceChecklist> items = evidenceChecklistMapper.selectList(
                new LambdaQueryWrapper<EvidenceChecklist>()
                        .eq(EvidenceChecklist::getCaseId, caseId)
                        .orderByAsc(EvidenceChecklist::getId));
        if (items.isEmpty()) {
            return new ArrayList<>();
        }

        // 案件全部证据（关联展示 + 推荐候选）
        List<CaseEvidence> evidences = caseEvidenceMapper.selectList(
                new LambdaQueryWrapper<CaseEvidence>()
                        .eq(CaseEvidence::getCaseId, caseId)
                        .orderByDesc(CaseEvidence::getCreatedAt));
        Map<Long, CaseEvidence> evidenceMap = evidences.stream()
                .collect(Collectors.toMap(CaseEvidence::getId, e -> e, (a, b) -> a));

        // 批量取关联关系
        List<Long> itemIds = items.stream().map(EvidenceChecklist::getId).collect(Collectors.toList());
        Map<Long, List<EvidenceChecklistRelation>> relMap = relationMapper.selectList(
                        new LambdaQueryWrapper<EvidenceChecklistRelation>()
                                .in(EvidenceChecklistRelation::getChecklistId, itemIds))
                .stream()
                .collect(Collectors.groupingBy(EvidenceChecklistRelation::getChecklistId));

        List<EvidenceChecklistVO> voList = new ArrayList<>();
        for (EvidenceChecklist item : items) {
            EvidenceChecklistVO vo = new EvidenceChecklistVO();
            BeanUtils.copyProperties(item, vo);

            List<EvidenceChecklistRelation> rels = relMap.getOrDefault(item.getId(), List.of());
            List<EvidenceChecklistVO.RelatedEvidence> related = new ArrayList<>();
            Set<Long> relatedIds = new HashSet<>();
            for (EvidenceChecklistRelation rel : rels) {
                CaseEvidence ev = evidenceMap.get(rel.getEvidenceId());
                if (ev == null) {
                    continue; // 证据已删除
                }
                relatedIds.add(ev.getId());
                EvidenceChecklistVO.RelatedEvidence re = new EvidenceChecklistVO.RelatedEvidence();
                re.setId(ev.getId());
                re.setEvidenceNo(ev.getEvidenceNo());
                re.setEvidenceName(ev.getEvidenceName());
                re.setCategory(ev.getCategory());
                re.setOcrStatus(ev.getOcrStatus());
                related.add(re);
            }
            vo.setRelatedEvidences(related);

            // 推荐候选：名称相似度匹配（不含已关联的）
            vo.setSuggestedEvidenceIds(suggest(evidences, item, relatedIds));
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public EvidenceChecklistVO createItem(ChecklistCreateDTO dto) {
        caseService.checkCasePermission(dto.getCaseId());
        EvidenceChecklist item = EvidenceChecklist.builder()
                .caseId(dto.getCaseId())
                .evidenceName(dto.getEvidenceName().trim())
                .purpose(dto.getPurpose())
                .status(dto.getStatus() == null || !VALID_STATUSES.contains(dto.getStatus())
                        ? CaseConstant.CHECKLIST_STATUS_REQUIRED : dto.getStatus())
                .relatedFact(dto.getRelatedFact())
                .remark(dto.getRemark())
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        evidenceChecklistMapper.insert(item);
        return toVO(item);
    }

    @Override
    public EvidenceChecklistVO updateItem(ChecklistUpdateDTO dto) {
        EvidenceChecklist item = checkPermission(dto.getId());
        if (dto.getEvidenceName() != null && !dto.getEvidenceName().trim().isEmpty()) {
            item.setEvidenceName(dto.getEvidenceName().trim());
        }
        if (dto.getPurpose() != null) {
            item.setPurpose(dto.getPurpose());
        }
        if (dto.getStatus() != null) {
            if (!VALID_STATUSES.contains(dto.getStatus())) {
                throw new BusinessException(CaseConstant.CHECKLIST_STATUS_INVALID);
            }
            item.setStatus(dto.getStatus());
        }
        if (dto.getRelatedFact() != null) {
            item.setRelatedFact(dto.getRelatedFact());
        }
        if (dto.getRemark() != null) {
            item.setRemark(dto.getRemark());
        }
        item.setUpdatedAt(DateTime.now());
        evidenceChecklistMapper.updateById(item);
        return toVO(item);
    }

    @Override
    public Long deleteItem(Long id) {
        checkPermission(id);
        relationMapper.delete(new LambdaQueryWrapper<EvidenceChecklistRelation>()
                .eq(EvidenceChecklistRelation::getChecklistId, id));
        evidenceChecklistMapper.deleteById(id);
        return id;
    }

    // ===================== LIST-002 关联 =====================

    @Override
    public EvidenceChecklistVO relateEvidence(Long checklistId, Long evidenceId) {
        EvidenceChecklist item = checkPermission(checklistId);
        CaseEvidence evidence = caseEvidenceMapper.selectById(evidenceId);
        if (evidence == null || !evidence.getCaseId().equals(item.getCaseId())) {
            throw new BusinessException(CaseConstant.CHECKLIST_EVIDENCE_NOT_BELONG);
        }
        Long exists = relationMapper.selectCount(new LambdaQueryWrapper<EvidenceChecklistRelation>()
                .eq(EvidenceChecklistRelation::getChecklistId, checklistId)
                .eq(EvidenceChecklistRelation::getEvidenceId, evidenceId));
        if (exists > 0) {
            throw new BusinessException(CaseConstant.CHECKLIST_RELATION_EXISTS);
        }
        relationMapper.insert(EvidenceChecklistRelation.builder()
                .checklistId(checklistId)
                .evidenceId(evidenceId)
                .createdAt(DateTime.now())
                .build());

        // REQUIRED → UPLOADED（CONFIRMED/OPTIONAL 状态保持不变，由用户自主管理）
        if (CaseConstant.CHECKLIST_STATUS_REQUIRED.equals(item.getStatus())) {
            item.setStatus(CaseConstant.CHECKLIST_STATUS_UPLOADED);
            item.setUpdatedAt(DateTime.now());
            evidenceChecklistMapper.updateById(item);
        }
        return toVO(item);
    }

    @Override
    public EvidenceChecklistVO unrelateEvidence(Long checklistId, Long evidenceId) {
        EvidenceChecklist item = checkPermission(checklistId);
        int deleted = relationMapper.delete(new LambdaQueryWrapper<EvidenceChecklistRelation>()
                .eq(EvidenceChecklistRelation::getChecklistId, checklistId)
                .eq(EvidenceChecklistRelation::getEvidenceId, evidenceId));
        if (deleted == 0) {
            throw new BusinessException(CaseConstant.CHECKLIST_RELATION_NOT_EXIST);
        }
        // 无其他关联且当前为 UPLOADED → 回退 REQUIRED
        Long remain = relationMapper.selectCount(new LambdaQueryWrapper<EvidenceChecklistRelation>()
                .eq(EvidenceChecklistRelation::getChecklistId, checklistId));
        if (remain == 0 && CaseConstant.CHECKLIST_STATUS_UPLOADED.equals(item.getStatus())) {
            item.setStatus(CaseConstant.CHECKLIST_STATUS_REQUIRED);
            item.setUpdatedAt(DateTime.now());
            evidenceChecklistMapper.updateById(item);
        }
        return toVO(item);
    }

    @Override
    public EvidenceChecklist checkPermission(Long checklistId) {
        EvidenceChecklist item = evidenceChecklistMapper.selectById(checklistId);
        if (item == null) {
            throw new BusinessException(CaseConstant.CHECKLIST_NOT_EXIST);
        }
        caseService.checkCasePermission(item.getCaseId());
        return item;
    }

    // ===================== 内部工具 =====================

    /**
     * 推荐候选证据：清单名称与证据名称/证据类型的字符重叠度匹配。
     * 轻量算法（不调 LLM），仅作展示建议，由用户确认关联。
     */
    private List<Long> suggest(List<CaseEvidence> evidences, EvidenceChecklist item, Set<Long> excludeIds) {
        List<Long> result = new ArrayList<>();
        for (CaseEvidence ev : evidences) {
            if (excludeIds.contains(ev.getId())) {
                continue;
            }
            double nameScore = overlapRatio(item.getEvidenceName(), ev.getEvidenceName());
            boolean typeHit = containsEither(item.getEvidenceName(), ev.getEvidenceName());
            if (nameScore >= SUGGESTION_THRESHOLD || typeHit) {
                result.add(ev.getId());
            }
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    /** 短字符串字符在长字符串中的覆盖率（去重字符集） */
    private double overlapRatio(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<Character> charsA = a.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        Set<Character> charsB = b.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        long hit = charsA.stream().filter(charsB::contains).count();
        return (double) hit / Math.max(1, Math.min(charsA.size(), charsB.size()));
    }

    private boolean containsEither(String a, String b) {
        return (a != null && b != null) && (a.contains(b) || b.contains(a));
    }

    private String buildFactText(List<CaseFact> facts) {
        if (facts.isEmpty()) {
            return "（暂无案件事实）";
        }
        StringBuilder sb = new StringBuilder();
        for (CaseFact f : facts) {
            sb.append("- ").append(truncate(f.getFactContent(), 200)).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildEvidenceText(List<CaseEvidence> evidences) {
        if (evidences.isEmpty()) {
            return "（暂无已上传证据）";
        }
        StringBuilder sb = new StringBuilder();
        for (CaseEvidence e : evidences) {
            sb.append("- ").append(e.getEvidenceName())
                    .append("（分类：").append(nullToEmpty(e.getCategory()))
                    .append("）\n");
        }
        return sb.toString().trim();
    }

    /** 证据分析摘要：每条证据最新分析的证据类型 + 综合结论（无分析则跳过） */
    private String buildAnalysisSummary(List<CaseEvidence> evidences) {
        StringBuilder sb = new StringBuilder();
        for (CaseEvidence ev : evidences) {
            EvidenceAnalysis analysis = evidenceAnalysisMapper.selectOne(
                    new LambdaQueryWrapper<EvidenceAnalysis>()
                            .eq(EvidenceAnalysis::getEvidenceId, ev.getId())
                            .orderByDesc(EvidenceAnalysis::getCreatedAt)
                            .last("LIMIT 1"));
            if (analysis == null) {
                continue;
            }
            sb.append("- ").append(ev.getEvidenceName())
                    .append("：类型=").append(nullToEmpty(analysis.getEvidenceType()));
            if (analysis.getAnalysisResult() != null && !analysis.getAnalysisResult().isEmpty()) {
                sb.append("，结论=").append(truncate(analysis.getAnalysisResult(), 150));
            }
            sb.append("\n");
        }
        return sb.length() == 0 ? "（暂无证据分析结果）" : sb.toString().trim();
    }

    /**
     * 法律检索参考（LIST-001 输入要求）：混合检索与案件相关的法律条款。
     * ES/Embedding 不可用时降级为空提示，不影响清单生成。
     */
    private String buildRagReference(LegalCase legalCase, List<CaseFact> facts) {
        try {
            StringBuilder queryBuilder = new StringBuilder(nullToEmpty(legalCase.getCaseType()));
            facts.stream().limit(3).forEach(f -> queryBuilder.append(" ").append(f.getFactContent()));
            List<HybridSearchResult> results = hybridSearchService.hybridSearch(
                    queryBuilder.toString(), null, RAG_REFERENCE_COUNT);
            if (results == null || results.isEmpty()) {
                return "（未检索到相关法律条款）";
            }
            StringBuilder sb = new StringBuilder();
            for (HybridSearchResult r : results) {
                sb.append("- ").append(nullToEmpty(r.getLawName()))
                        .append(" ").append(nullToEmpty(r.getArticleNo()))
                        .append("：").append(truncate(nullToEmpty(r.getContent()), 120))
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("证据清单生成的法律检索降级（检索服务不可用）：{}", e.getMessage());
            return "（检索服务暂不可用，已跳过法律参考）";
        }
    }

    private EvidenceChecklistVO toVO(EvidenceChecklist item) {
        EvidenceChecklistVO vo = new EvidenceChecklistVO();
        BeanUtils.copyProperties(item, vo);
        vo.setRelatedEvidences(new ArrayList<>());
        vo.setSuggestedEvidenceIds(new ArrayList<>());
        return vo;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String truncate(String text) {
        return truncate(text, 200);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
