package org.lexagent.service;

import org.lexagent.model.dto.EvidenceQueryDTO;
import org.lexagent.model.dto.EvidenceUpdateDTO;
import org.lexagent.model.vo.EvidenceVO;
import org.lexagent.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 案件证据管理服务（V2.0 证据管理模块）
 * 证据上传 / 分类 / 列表 / 详情 / 删除 / OCR 识别与重新识别
 */
public interface CaseEvidenceService {

    /** 上传证据（EVIDENCE-001）：校验类型与大小，保存文件并异步触发 OCR/解析 */
    EvidenceVO uploadEvidence(Long caseId, MultipartFile file, String evidenceName,
                              String category, String remark);

    /** 分页查询证据列表（EVIDENCE-003）：普通用户仅查自己的证据，管理员可查全部 */
    PageResult<EvidenceVO> queryEvidences(Integer pageNum, Integer pageSize, EvidenceQueryDTO queryDTO);

    /** 查询证据详情（EVIDENCE-005）：含 OCR 文本与修正文本 */
    EvidenceVO getEvidence(Long id);

    /** 修改证据信息（名称/分类/备注/修正文本） */
    EvidenceVO updateEvidence(EvidenceUpdateDTO dto);

    /** 删除证据：同时删除本地文件 */
    Long deleteEvidence(Long id);

    /** 重新 OCR 识别（EVIDENCE-004）：重置状态并异步触发 */
    EvidenceVO retryOcr(Long id);

    /** 获取 OCR 识别文本（GET /evidence/{id}/ocr） */
    String getOcrText(Long id);

    /** 校验证据访问权限：属主或管理员，否则抛异常 */
    org.lexagent.model.entity.CaseEvidence checkEvidencePermission(Long evidenceId);
}
