import request from './request'

// ===== 智能证据清单（V2.0 阶段四） =====

/** 生成证据清单（LLM，同步返回；msg 为整体说明） */
export function generateChecklist(caseId) {
  return request.post('/evidence-checklist/generate', null, {
    params: { caseId },
    timeout: 120000
  })
}

/** 查询证据清单（含关联证据与推荐候选） */
export function getChecklist(caseId) {
  return request.get(`/evidence-checklist/${caseId}`)
}

/** 手动新增清单项目 */
export function createChecklistItem(data) {
  return request.post('/evidence-checklist/create', data)
}

/** 修改清单项目 */
export function updateChecklistItem(data) {
  return request.put('/evidence-checklist/update', data)
}

/** 删除清单项目 */
export function deleteChecklistItem(id) {
  return request.delete(`/evidence-checklist/${id}`)
}

/** 关联实际证据 */
export function relateChecklistEvidence(checklistId, evidenceId) {
  return request.post('/evidence-checklist/relation', { checklistId, evidenceId })
}

/** 取消关联实际证据 */
export function unrelateChecklistEvidence(checklistId, evidenceId) {
  return request.delete('/evidence-checklist/relation', {
    params: { checklistId, evidenceId }
  })
}
