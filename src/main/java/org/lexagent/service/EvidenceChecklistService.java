package org.lexagent.service;

import org.lexagent.model.dto.ChecklistCreateDTO;
import org.lexagent.model.dto.ChecklistUpdateDTO;
import org.lexagent.model.vo.EvidenceChecklistVO;

import java.util.List;

/**
 * 证据清单服务（V2.0 阶段四 / LIST-001~004）
 *
 * 职责：
 * - LIST-001 证据清单生成（结合案件事实、已有证据与法律检索，LLM 生成个性化清单）
 * - LIST-002 状态管理（REQUIRED/UPLOADED/CONFIRMED/OPTIONAL）与证据关联推荐
 * - LIST-003 证据缺失提醒（清单中未关联证据的项目即缺失项）
 * - LIST-004 清单修改（新增/删除/改名称/用途/状态/备注）
 */
public interface EvidenceChecklistService {

    /** 生成证据清单（LIST-001）：同名项目跳过以保留用户编辑，其余插入；返回清单与整体说明 */
    org.lexagent.result.Result<java.util.List<EvidenceChecklistVO>> generate(Long caseId);

    /** 查询案件证据清单（含关联证据与推荐关联候选） */
    List<EvidenceChecklistVO> getByCase(Long caseId);

    /** 手动新增清单项目（LIST-004） */
    EvidenceChecklistVO createItem(ChecklistCreateDTO dto);

    /** 修改清单项目（LIST-004）：名称/用途/状态/关联事实/备注，留空不修改 */
    EvidenceChecklistVO updateItem(ChecklistUpdateDTO dto);

    /** 删除清单项目（级联删除关联关系） */
    Long deleteItem(Long id);

    /** 关联实际证据（LIST-002）：REQUIRED 状态自动置为 UPLOADED */
    EvidenceChecklistVO relateEvidence(Long checklistId, Long evidenceId);

    /** 取消关联实际证据：UPLOADED 状态自动回退为 REQUIRED */
    EvidenceChecklistVO unrelateEvidence(Long checklistId, Long evidenceId);

    /** 校验清单项目访问权限：属主或管理员 */
    org.lexagent.model.entity.EvidenceChecklist checkPermission(Long checklistId);
}
