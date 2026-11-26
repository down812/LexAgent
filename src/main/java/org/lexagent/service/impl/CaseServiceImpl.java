package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.CaseConstant;
import org.lexagent.constants.RagConstant;
import org.lexagent.context.LoginContext;
import org.lexagent.exception.BusinessException;
import org.lexagent.exception.BusinessNotFoundException;
import org.lexagent.exception.PermissionDeniedException;
import org.lexagent.mapper.CaseConversationMapper;
import org.lexagent.mapper.CaseEvidenceMapper;
import org.lexagent.mapper.CaseFactMapper;
import org.lexagent.mapper.ConversationMapper;
import org.lexagent.mapper.LegalCaseMapper;
import org.lexagent.model.dto.CaseCreateDTO;
import org.lexagent.model.dto.CaseFactCreateDTO;
import org.lexagent.model.dto.CaseQueryDTO;
import org.lexagent.model.dto.CaseUpdateDTO;
import org.lexagent.model.entity.CaseConversation;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.model.entity.CaseFact;
import org.lexagent.model.entity.Conversation;
import org.lexagent.model.entity.LegalCase;
import org.lexagent.model.vo.CaseConversationVO;
import org.lexagent.model.vo.CaseFactVO;
import org.lexagent.model.vo.CaseVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.CaseService;
import org.lexagent.service.CaseEvidenceService;
import org.lexagent.utils.FileStorageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 案件管理服务实现（V2.0）
 *
 * 数据权限：普通用户仅能访问自己创建的案件；管理员（type 0/1）可访问全部案件。
 * 删除案件：级联删除案件事实、会话关联、证据记录及证据本地文件。
 */
@Slf4j
@Service
public class CaseServiceImpl implements CaseService {

    @Autowired
    private LegalCaseMapper legalCaseMapper;

    @Autowired
    private CaseFactMapper caseFactMapper;

    @Autowired
    private CaseConversationMapper caseConversationMapper;

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private CaseEvidenceService caseEvidenceService;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    // ===================== 案件 CRUD =====================

    @Override
    public Result<CaseVO> createCase(CaseCreateDTO dto) {
        Long userId = LoginContext.getUserId();
        LegalCase record = LegalCase.builder()
                .userId(userId)
                .caseName(dto.getCaseName().trim())
                .caseType(dto.getCaseType().trim())
                .description(dto.getDescription())
                .occurredAt(dto.getOccurredAt())
                .location(dto.getLocation())
                .partiesInvolved(dto.getPartiesInvolved())
                .remark(dto.getRemark())
                .status(CaseConstant.CASE_STATUS_ACTIVE)
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        legalCaseMapper.insert(record);

        // 生成案件编号：CASE + yyyyMMdd + 案件ID（补零6位）
        String caseNo = CaseConstant.CASE_NO_PREFIX
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", record.getId());
        record.setCaseNo(caseNo);
        legalCaseMapper.updateById(record);

        log.info("用户[{}]创建案件成功：id={}, caseNo={}, name={}", userId, record.getId(), caseNo, record.getCaseName());
        return Result.success(toVO(record));
    }

    @Override
    public PageResult<CaseVO> queryCases(Integer pageNum, Integer pageSize, CaseQueryDTO queryDTO) {
        Page<LegalCase> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LegalCase> wrapper = new LambdaQueryWrapper<>();

        // 数据权限：普通用户只能看自己的案件，管理员可看全部
        if (!isAdmin()) {
            wrapper.eq(LegalCase::getUserId, LoginContext.getUserId());
        }
        if (queryDTO != null) {
            if (queryDTO.getCaseName() != null && !queryDTO.getCaseName().trim().isEmpty()) {
                wrapper.like(LegalCase::getCaseName, queryDTO.getCaseName().trim());
            }
            if (queryDTO.getCaseType() != null && !queryDTO.getCaseType().trim().isEmpty()) {
                wrapper.eq(LegalCase::getCaseType, queryDTO.getCaseType().trim());
            }
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().trim().isEmpty()) {
                wrapper.eq(LegalCase::getStatus, queryDTO.getStatus().trim());
            }
            if ("asc".equalsIgnoreCase(queryDTO.getOrder())) {
                wrapper.orderByAsc(LegalCase::getCreatedAt);
            } else {
                wrapper.orderByDesc(LegalCase::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(LegalCase::getCreatedAt);
        }
        Page<LegalCase> result = legalCaseMapper.selectPage(page, wrapper);

        List<CaseVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), result.getPages(), pageNum, pageSize, voList);
    }

    @Override
    public CaseVO getCase(Long id) {
        LegalCase record = checkCasePermission(id);
        CaseVO vo = toVO(record);
        // 详情补充证据与会话数量
        vo.setEvidenceCount(caseEvidenceMapper.selectCount(
                new LambdaQueryWrapper<CaseEvidence>().eq(CaseEvidence::getCaseId, id)));
        vo.setConversationCount(caseConversationMapper.selectCount(
                new LambdaQueryWrapper<CaseConversation>().eq(CaseConversation::getCaseId, id)));
        vo.setFactCount(caseFactMapper.selectCount(
                new LambdaQueryWrapper<CaseFact>().eq(CaseFact::getCaseId, id)));
        return vo;
    }

    @Override
    public CaseVO updateCase(CaseUpdateDTO dto) {
        LegalCase record = checkCasePermission(dto.getId());
        if (dto.getCaseName() != null && !dto.getCaseName().trim().isEmpty()) {
            record.setCaseName(dto.getCaseName().trim());
        }
        if (dto.getCaseType() != null && !dto.getCaseType().trim().isEmpty()) {
            record.setCaseType(dto.getCaseType().trim());
        }
        if (dto.getDescription() != null) {
            record.setDescription(dto.getDescription());
        }
        if (dto.getOccurredAt() != null) {
            record.setOccurredAt(dto.getOccurredAt());
        }
        if (dto.getLocation() != null) {
            record.setLocation(dto.getLocation());
        }
        if (dto.getPartiesInvolved() != null) {
            record.setPartiesInvolved(dto.getPartiesInvolved());
        }
        if (dto.getRemark() != null) {
            record.setRemark(dto.getRemark());
        }
        record.setUpdatedAt(DateTime.now());
        legalCaseMapper.updateById(record);
        return toVO(record);
    }

    @Override
    public Long archiveCase(Long id) {
        LegalCase record = checkCasePermission(id);
        record.setStatus(CaseConstant.CASE_STATUS_ARCHIVED);
        record.setUpdatedAt(DateTime.now());
        legalCaseMapper.updateById(record);
        log.info("案件[{}]已归档：caseNo={}", id, record.getCaseNo());
        return id;
    }

    @Override
    public Long deleteCase(Long id) {
        LegalCase record = checkCasePermission(id);
        // 1. 删除证据记录及本地文件
        List<CaseEvidence> evidences = caseEvidenceMapper.selectList(
                new LambdaQueryWrapper<CaseEvidence>().eq(CaseEvidence::getCaseId, id));
        for (CaseEvidence evidence : evidences) {
            try {
                caseEvidenceService.deleteEvidence(evidence.getId());
            } catch (Exception e) {
                log.warn("删除案件时清理证据[{}]失败，继续处理其余数据: {}", evidence.getId(), e.getMessage());
            }
        }
        // 2. 删除案件事实、会话关联、案件记录
        caseFactMapper.delete(new LambdaQueryWrapper<CaseFact>().eq(CaseFact::getCaseId, id));
        caseConversationMapper.delete(new LambdaQueryWrapper<CaseConversation>().eq(CaseConversation::getCaseId, id));
        legalCaseMapper.deleteById(id);
        log.info("用户[{}]删除案件[{}]（caseNo={}）及关联数据（证据 {} 条）",
                LoginContext.getUserId(), id, record.getCaseNo(), evidences.size());
        return id;
    }

    // ===================== 案件事实 =====================

    @Override
    public List<CaseFactVO> getFacts(Long caseId) {
        checkCasePermission(caseId);
        List<CaseFact> facts = caseFactMapper.selectList(
                new LambdaQueryWrapper<CaseFact>()
                        .eq(CaseFact::getCaseId, caseId)
                        .orderByAsc(CaseFact::getCreatedAt));
        return facts.stream().map(this::toFactVO).collect(Collectors.toList());
    }

    @Override
    public Result<CaseFactVO> addFact(Long caseId, CaseFactCreateDTO dto) {
        checkCasePermission(caseId);
        CaseFact fact = CaseFact.builder()
                .caseId(caseId)
                .factContent(dto.getFactContent().trim())
                .factSource(CaseConstant.FACT_SOURCE_USER_INPUT)
                .confidence(BigDecimal.valueOf(100))
                .confirmed(1) // 用户手动输入的事实视为已确认
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        caseFactMapper.insert(fact);
        log.info("用户[{}]为案件[{}]新增事实：factId={}", LoginContext.getUserId(), caseId, fact.getId());
        return Result.success(toFactVO(fact));
    }

    @Override
    public Long deleteFact(Long factId) {
        CaseFact fact = caseFactMapper.selectById(factId);
        if (fact == null) {
            throw new BusinessNotFoundException("案件事实不存在");
        }
        // 仅属主或管理员可删（通过案件权限校验）
        checkCasePermission(fact.getCaseId());
        caseFactMapper.deleteById(factId);
        return factId;
    }

    // ===================== 案件-会话关联 =====================

    @Override
    public List<CaseConversationVO> getConversations(Long caseId) {
        checkCasePermission(caseId);
        List<CaseConversation> relations = caseConversationMapper.selectList(
                new LambdaQueryWrapper<CaseConversation>()
                        .eq(CaseConversation::getCaseId, caseId)
                        .orderByDesc(CaseConversation::getCreatedAt));
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询会话信息
        List<Long> convIds = relations.stream()
                .map(CaseConversation::getConversationId)
                .collect(Collectors.toList());
        Map<Long, Conversation> convMap = conversationMapper.selectBatchIds(convIds).stream()
                .collect(Collectors.toMap(Conversation::getId, Function.identity(), (a, b) -> a));

        return relations.stream().map(rel -> {
            CaseConversationVO vo = new CaseConversationVO();
            vo.setRelationId(rel.getId());
            vo.setCaseId(rel.getCaseId());
            vo.setConversationId(rel.getConversationId());
            vo.setLinkedAt(rel.getCreatedAt());
            Conversation conv = convMap.get(rel.getConversationId());
            if (conv != null) {
                vo.setConversationUuid(conv.getConversationId());
                vo.setTitle(conv.getTitle());
                vo.setStatus(conv.getStatus());
                vo.setCreatedAt(conv.getCreatedAt());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Long linkConversation(Long caseId, Long conversationId) {
        LegalCase record = checkCasePermission(caseId);
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !Objects.equals(conv.getUserId(), LoginContext.getUserId())) {
            throw new BusinessNotFoundException(CaseConstant.CONVERSATION_NOT_EXIST);
        }
        Long exists = caseConversationMapper.selectCount(
                new LambdaQueryWrapper<CaseConversation>()
                        .eq(CaseConversation::getCaseId, caseId)
                        .eq(CaseConversation::getConversationId, conversationId));
        if (exists > 0) {
            throw new BusinessException(CaseConstant.CONVERSATION_ALREADY_LINKED);
        }
        CaseConversation relation = CaseConversation.builder()
                .caseId(caseId)
                .conversationId(conversationId)
                .createdAt(DateTime.now())
                .build();
        caseConversationMapper.insert(relation);
        log.info("会话[{}]已关联到案件[{}]（caseNo={}）", conversationId, caseId, record.getCaseNo());
        return relation.getId();
    }

    @Override
    public void linkConversationIfAbsent(Long caseId, Long conversationId) {
        try {
            Long exists = caseConversationMapper.selectCount(
                    new LambdaQueryWrapper<CaseConversation>()
                            .eq(CaseConversation::getCaseId, caseId)
                            .eq(CaseConversation::getConversationId, conversationId));
            if (exists > 0) {
                return;
            }
            caseConversationMapper.insert(CaseConversation.builder()
                    .caseId(caseId)
                    .conversationId(conversationId)
                    .createdAt(DateTime.now())
                    .build());
            log.info("会话[{}]已静默关联到案件[{}]", conversationId, caseId);
        } catch (Exception e) {
            // 关联失败不影响问诊主流程
            log.warn("案件会话静默关联失败：caseId={}, conversationId={}, 原因={}", caseId, conversationId, e.getMessage());
        }
    }

    // ===================== 权限与转换 =====================

    @Override
    public LegalCase checkCasePermission(Long caseId) {
        LegalCase record = legalCaseMapper.selectById(caseId);
        if (record == null) {
            throw new BusinessNotFoundException(CaseConstant.CASE_NOT_EXIST);
        }
        if (!isAdmin() && !Objects.equals(record.getUserId(), LoginContext.getUserId())) {
            throw new PermissionDeniedException(CaseConstant.CASE_NO_PERMISSION);
        }
        return record;
    }

    private boolean isAdmin() {
        Integer type = LoginContext.getUserType();
        return type != null && type <= org.lexagent.Enum.UserEnum.ADMIN.getCode();
    }

    private CaseVO toVO(LegalCase record) {
        CaseVO vo = new CaseVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    private CaseFactVO toFactVO(CaseFact fact) {
        CaseFactVO vo = new CaseFactVO();
        BeanUtils.copyProperties(fact, vo);
        return vo;
    }
}
