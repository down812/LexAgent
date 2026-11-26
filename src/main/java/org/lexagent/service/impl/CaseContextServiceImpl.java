package org.lexagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.CaseConstant;
import org.lexagent.constants.RagConstant;
import org.lexagent.mapper.CaseEvidenceMapper;
import org.lexagent.mapper.CaseFactMapper;
import org.lexagent.mapper.EvidenceAnalysisMapper;
import org.lexagent.mapper.EvidenceChecklistMapper;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.model.entity.CaseFact;
import org.lexagent.model.entity.EvidenceAnalysis;
import org.lexagent.model.entity.EvidenceChecklist;
import org.lexagent.model.entity.LegalCase;
import org.lexagent.model.vo.CaseContextVO;
import org.lexagent.service.CaseContextService;
import org.lexagent.service.CaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 案件上下文组装服务实现（V2.0 阶段五 / CHAT-001）
 *
 * 数据隔离（CHAT-003）：
 *   通过 CaseService.checkCasePermission 校验属主/管理员权限，
 *   证据概览仅取该案件（case_id）下的数据，案件之间的上下文天然隔离。
 */
@Slf4j
@Service
public class CaseContextServiceImpl implements CaseContextService {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseFactMapper caseFactMapper;

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private EvidenceAnalysisMapper evidenceAnalysisMapper;

    @Autowired
    private EvidenceChecklistMapper evidenceChecklistMapper;

    @Override
    public CaseContextVO load(Long caseId) {
        LegalCase legalCase = caseService.checkCasePermission(caseId);

        // 已确认事实（含用户输入与已确认的 AI 提取；未确认的 AI 推测不作为既定事实注入）
        List<CaseFact> facts = caseFactMapper.selectList(
                new LambdaQueryWrapper<CaseFact>()
                        .eq(CaseFact::getCaseId, caseId)
                        .orderByAsc(CaseFact::getCreatedAt));
        List<String> confirmedFacts = facts.stream()
                .filter(f -> f.getConfirmed() != null && f.getConfirmed() == 1)
                .map(CaseFact::getFactContent)
                .toList();

        // 证据概览（含最新分析摘要）
        List<CaseEvidence> evidences = caseEvidenceMapper.selectList(
                new LambdaQueryWrapper<CaseEvidence>()
                        .eq(CaseEvidence::getCaseId, caseId)
                        .orderByAsc(CaseEvidence::getCreatedAt));
        List<CaseContextVO.EvidenceBrief> briefs = new ArrayList<>();
        for (CaseEvidence ev : evidences) {
            String evidenceType = null;
            String summary = null;
            if (RagConstant.FILE_STATUS_SUCCESS.equals(ev.getOcrStatus())) {
                EvidenceAnalysis analysis = evidenceAnalysisMapper.selectOne(
                        new LambdaQueryWrapper<EvidenceAnalysis>()
                                .eq(EvidenceAnalysis::getEvidenceId, ev.getId())
                                .orderByDesc(EvidenceAnalysis::getCreatedAt)
                                .last("LIMIT 1"));
                if (analysis != null) {
                    evidenceType = analysis.getEvidenceType();
                    summary = analysis.getAnalysisResult();
                }
            }
            briefs.add(CaseContextVO.EvidenceBrief.builder()
                    .evidenceName(ev.getEvidenceName())
                    .category(ev.getCategory())
                    .ocrStatus(ev.getOcrStatus())
                    .evidenceType(evidenceType)
                    .analysisSummary(summary)
                    .build());
        }

        // 证据清单概要
        List<EvidenceChecklist> checklist = evidenceChecklistMapper.selectList(
                new LambdaQueryWrapper<EvidenceChecklist>()
                        .eq(EvidenceChecklist::getCaseId, caseId)
                        .orderByAsc(EvidenceChecklist::getId));
        List<String> checklistItems = checklist.stream()
                .map(c -> c.getEvidenceName() + "（" + c.getStatus() + "）")
                .toList();

        CaseContextVO context = CaseContextVO.builder()
                .caseId(caseId)
                .caseName(legalCase.getCaseName())
                .caseNo(legalCase.getCaseNo())
                .caseType(legalCase.getCaseType())
                .description(legalCase.getDescription())
                .confirmedFacts(confirmedFacts)
                .evidences(briefs)
                .checklistItems(checklistItems)
                .build();
        log.info("[CaseContext] 案件上下文加载完成：caseId={}, facts={}, evidences={}, checklist={}",
                caseId, confirmedFacts.size(), briefs.size(), checklistItems.size());
        return context;
    }
}
