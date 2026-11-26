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
import org.lexagent.mapper.CaseEvidenceMapper;
import org.lexagent.mapper.LegalCaseMapper;
import org.lexagent.model.dto.EvidenceQueryDTO;
import org.lexagent.model.dto.EvidenceUpdateDTO;
import org.lexagent.model.entity.CaseEvidence;
import org.lexagent.model.entity.LegalCase;
import org.lexagent.model.vo.EvidenceVO;
import org.lexagent.result.PageResult;
import org.lexagent.service.CaseEvidenceService;
import org.lexagent.service.EvidenceProcessTaskService;
import org.lexagent.utils.FileStorageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 案件证据管理服务实现（V2.0 证据管理模块）
 *
 * 上传流程：校验案件权限 → 校验文件类型/大小 → 本地存储 → 建证据记录（WAITING）
 *   → 异步触发 OCR/解析（EvidenceProcessTaskService）
 *
 * 注意：与知识库文件上传一致，不用 @Transactional，
 * insert 需独立提交后异步线程才能查到记录。
 */
@Slf4j
@Service
public class CaseEvidenceServiceImpl implements CaseEvidenceService {

    /** 证据允许的扩展名（需求文档 EVIDENCE-001） */
    private static final Set<String> EVIDENCE_SUPPORTED_EXT =
            Set.of("jpg", "jpeg", "png", "bmp", "webp", "pdf", "docx", "doc", "txt");

    @Autowired
    private CaseEvidenceMapper caseEvidenceMapper;

    @Autowired
    private LegalCaseMapper legalCaseMapper;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private EvidenceProcessTaskService evidenceProcessTaskService;

    @Override
    public EvidenceVO uploadEvidence(Long caseId, MultipartFile file, String evidenceName,
                                     String category, String remark) {
        // 1. 校验案件存在且有权访问
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if (legalCase == null) {
            throw new BusinessNotFoundException(CaseConstant.CASE_NOT_EXIST);
        }
        if (!isAdmin() && !Objects.equals(legalCase.getUserId(), LoginContext.getUserId())) {
            throw new PermissionDeniedException(CaseConstant.CASE_NO_PERMISSION);
        }

        // 2. 校验文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CaseConstant.EVIDENCE_FILE_EMPTY);
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = getExtension(originalName);
        if (!EVIDENCE_SUPPORTED_EXT.contains(ext)) {
            throw new BusinessException(CaseConstant.EVIDENCE_FILE_TYPE_NOT_SUPPORT);
        }
        if (file.getSize() > CaseConstant.EVIDENCE_MAX_FILE_SIZE) {
            throw new BusinessException(CaseConstant.EVIDENCE_FILE_TOO_LARGE);
        }

        // 3. 存储文件到本地
        String relativePath;
        try {
            relativePath = fileStorageUtil.store(file);
        } catch (Exception e) {
            log.error("证据文件存储失败", e);
            throw new BusinessException("证据文件存储失败: " + e.getMessage());
        }

        // 4. 生成证据记录（WAITING 状态）
        String name = (evidenceName == null || evidenceName.trim().isEmpty())
                ? stripExtension(originalName)
                : evidenceName.trim();
        CaseEvidence record = CaseEvidence.builder()
                .caseId(caseId)
                .userId(LoginContext.getUserId())
                .evidenceName(name)
                .originalName(originalName)
                .fileType(resolveEvidenceFileType(ext))
                .filePath(relativePath)
                .category(category == null || category.trim().isEmpty()
                        ? CaseConstant.DEFAULT_EVIDENCE_CATEGORY : category.trim())
                .ocrStatus(RagConstant.FILE_STATUS_WAITING)
                .remark(remark)
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        caseEvidenceMapper.insert(record);

        // 生成证据编号：EV + yyyyMMdd + ID（补零6位）
        String evidenceNo = CaseConstant.EVIDENCE_NO_PREFIX
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", record.getId());
        record.setEvidenceNo(evidenceNo);
        caseEvidenceMapper.updateById(record);

        // 5. 异步触发 OCR / 文档解析（insert 提交后再调用，否则异步线程查不到记录）
        log.info("证据[{}]上传成功：caseNo={}, 状态=WAITING，异步触发识别", record.getId(), legalCase.getCaseNo());
        evidenceProcessTaskService.processAsync(record.getId());

        return toVO(record);
    }

    @Override
    public PageResult<EvidenceVO> queryEvidences(Integer pageNum, Integer pageSize, EvidenceQueryDTO queryDTO) {
        Page<CaseEvidence> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CaseEvidence> wrapper = new LambdaQueryWrapper<>();

        // 数据权限：普通用户只能查自己的证据，管理员可查全部
        if (!isAdmin()) {
            wrapper.eq(CaseEvidence::getUserId, LoginContext.getUserId());
        }
        if (queryDTO != null) {
            if (queryDTO.getCaseId() != null) {
                wrapper.eq(CaseEvidence::getCaseId, queryDTO.getCaseId());
            }
            if (queryDTO.getEvidenceName() != null && !queryDTO.getEvidenceName().trim().isEmpty()) {
                wrapper.like(CaseEvidence::getEvidenceName, queryDTO.getEvidenceName().trim());
            }
            if (queryDTO.getCategory() != null && !queryDTO.getCategory().trim().isEmpty()) {
                wrapper.eq(CaseEvidence::getCategory, queryDTO.getCategory().trim());
            }
            if (queryDTO.getOcrStatus() != null && !queryDTO.getOcrStatus().trim().isEmpty()) {
                wrapper.eq(CaseEvidence::getOcrStatus, queryDTO.getOcrStatus().trim());
            }
        }
        wrapper.orderByDesc(CaseEvidence::getCreatedAt);
        Page<CaseEvidence> result = caseEvidenceMapper.selectPage(page, wrapper);

        List<EvidenceVO> voList = result.getRecords().stream()
                .map(this::toVO) // 列表不带 ocr_text 大字段
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), result.getPages(), pageNum, pageSize, voList);
    }

    @Override
    public EvidenceVO getEvidence(Long id) {
        CaseEvidence record = checkEvidencePermission(id);
        return toDetailVO(record);
    }

    @Override
    public EvidenceVO updateEvidence(EvidenceUpdateDTO dto) {
        CaseEvidence record = checkEvidencePermission(dto.getId());
        if (dto.getEvidenceName() != null && !dto.getEvidenceName().trim().isEmpty()) {
            record.setEvidenceName(dto.getEvidenceName().trim());
        }
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            record.setCategory(dto.getCategory().trim());
        }
        if (dto.getRemark() != null) {
            record.setRemark(dto.getRemark());
        }
        if (dto.getCorrectedText() != null) {
            // 用户修正文本单独保存，不覆盖原始识别文本（EVIDENCE-004 要求）
            record.setCorrectedText(dto.getCorrectedText());
        }
        record.setUpdatedAt(DateTime.now());
        caseEvidenceMapper.updateById(record);
        return toDetailVO(record);
    }

    @Override
    public Long deleteEvidence(Long id) {
        CaseEvidence record = checkEvidencePermission(id);
        // 删除本地文件
        try {
            File f = new File(fileStorageUtil.absolutePath(record.getFilePath()));
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
            log.warn("删除证据本地文件失败: {}", e.getMessage());
        }
        caseEvidenceMapper.deleteById(id);
        log.info("证据[{}]已删除：evidenceNo={}", id, record.getEvidenceNo());
        return id;
    }

    @Override
    public EvidenceVO retryOcr(Long id) {
        CaseEvidence record = checkEvidencePermission(id);
        record.setOcrStatus(RagConstant.FILE_STATUS_WAITING);
        record.setUpdatedAt(DateTime.now());
        caseEvidenceMapper.updateById(record);
        log.info("证据[{}]触发重新识别", id);
        evidenceProcessTaskService.processAsync(id);
        return toVO(record);
    }

    @Override
    public String getOcrText(Long id) {
        CaseEvidence record = checkEvidencePermission(id);
        if (record.getOcrText() == null || record.getOcrText().isEmpty()) {
            throw new BusinessException(CaseConstant.OCR_TEXT_EMPTY);
        }
        return record.getOcrText();
    }

    @Override
    public CaseEvidence checkEvidencePermission(Long evidenceId) {
        CaseEvidence record = caseEvidenceMapper.selectById(evidenceId);
        if (record == null) {
            throw new BusinessNotFoundException(CaseConstant.EVIDENCE_NOT_EXIST);
        }
        if (!isAdmin() && !Objects.equals(record.getUserId(), LoginContext.getUserId())) {
            throw new PermissionDeniedException(CaseConstant.EVIDENCE_NO_PERMISSION);
        }
        return record;
    }

    // ===================== 内部工具 =====================

    private boolean isAdmin() {
        Integer type = LoginContext.getUserType();
        return type != null && type <= org.lexagent.Enum.UserEnum.ADMIN.getCode();
    }

    private String resolveEvidenceFileType(String ext) {
        switch (ext) {
            case "pdf":
                return RagConstant.FILE_TYPE_PDF;
            case "doc":
                return RagConstant.FILE_TYPE_DOC;
            case "docx":
                return RagConstant.FILE_TYPE_DOCX;
            case "txt":
                return RagConstant.FILE_TYPE_TXT;
            default:
                return RagConstant.FILE_TYPE_IMAGE;
        }
    }

    private String getExtension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase();
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /** 列表 VO：不携带 ocr_text / corrected_text 大字段 */
    private EvidenceVO toVO(CaseEvidence record) {
        EvidenceVO vo = new EvidenceVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    /** 详情 VO：携带 OCR 文本与修正文本 */
    private EvidenceVO toDetailVO(CaseEvidence record) {
        EvidenceVO vo = toVO(record);
        vo.setOcrText(record.getOcrText());
        vo.setCorrectedText(record.getCorrectedText());
        return vo;
    }
}
