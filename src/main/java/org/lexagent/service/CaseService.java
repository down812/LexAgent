package org.lexagent.service;

import org.lexagent.model.dto.CaseCreateDTO;
import org.lexagent.model.dto.CaseFactCreateDTO;
import org.lexagent.model.dto.CaseQueryDTO;
import org.lexagent.model.dto.CaseUpdateDTO;
import org.lexagent.model.entity.LegalCase;
import org.lexagent.model.vo.CaseConversationVO;
import org.lexagent.model.vo.CaseFactVO;
import org.lexagent.model.vo.CaseVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;

import java.util.List;

/**
 * 案件管理服务（V2.0）
 * 案件创建 / 列表 / 详情 / 编辑 / 归档删除 / 案件事实 / 会话关联
 */
public interface CaseService {

    /** 创建案件（CASE-001）：保存并生成案件编号 */
    Result<CaseVO> createCase(CaseCreateDTO dto);

    /** 分页查询案件列表（CASE-002）：普通用户仅查自己的案件，管理员可查全部 */
    PageResult<CaseVO> queryCases(Integer pageNum, Integer pageSize, CaseQueryDTO queryDTO);

    /** 查询案件详情（CASE-003） */
    CaseVO getCase(Long id);

    /** 修改案件基本信息（CASE-004） */
    CaseVO updateCase(CaseUpdateDTO dto);

    /** 归档案件（CASE-005）：归档后保留全部历史数据 */
    Long archiveCase(Long id);

    /** 删除案件：级联删除案件事实/会话关联/证据记录与本地文件 */
    Long deleteCase(Long id);

    /** 查询案件事实列表（GET /case/{id}/facts） */
    List<CaseFactVO> getFacts(Long caseId);

    /** 新增案件事实（POST /case/{id}/fact）：来源 USER_INPUT，视为已确认 */
    Result<CaseFactVO> addFact(Long caseId, CaseFactCreateDTO dto);

    /** 删除案件事实 */
    Long deleteFact(Long factId);

    /** 查询案件关联的问诊会话（GET /case/{id}/conversations） */
    List<CaseConversationVO> getConversations(Long caseId);

    /** 将已有会话关联到案件（POST /case/{id}/conversation/{conversationId}） */
    Long linkConversation(Long caseId, Long conversationId);

    /**
     * V2.0 静默关联（CHAT-003）：问诊链路中会话与案件关联，已存在则跳过，不抛业务异常
     */
    void linkConversationIfAbsent(Long caseId, Long conversationId);

    /** 校验案件访问权限：属主或管理员可访问，否则抛异常 */
    LegalCase checkCasePermission(Long caseId);
}
