import request from './request'

// ===== 案件管理（V2.0） =====

/** 创建案件 */
export function createCase(data) {
  return request.post('/case/create', data)
}

/** 分页查询案件列表 */
export function queryCases(params, data) {
  return request.post('/case/page', data, { params })
}

/** 查询案件详情 */
export function getCase(id) {
  return request.get(`/case/${id}`)
}

/** 修改案件 */
export function updateCase(data) {
  return request.put('/case/update', data)
}

/** 归档案件 */
export function archiveCase(id) {
  return request.delete(`/case/${id}/archive`)
}

/** 删除案件（级联删除事实/会话关联/证据） */
export function deleteCase(id) {
  return request.delete(`/case/${id}`)
}

// ===== 案件事实 =====

/** 查询案件事实 */
export function getCaseFacts(id) {
  return request.get(`/case/${id}/facts`)
}

/** 新增案件事实 */
export function addCaseFact(id, factContent) {
  return request.post(`/case/${id}/fact`, { factContent })
}

/** 删除案件事实 */
export function deleteCaseFact(factId) {
  return request.delete(`/case/fact/${factId}`)
}

// ===== 案件-问诊会话关联 =====

/** 查询案件关联的问诊会话 */
export function getCaseConversations(id) {
  return request.get(`/case/${id}/conversations`)
}

/** 关联已有会话到案件 */
export function linkConversation(caseId, conversationId) {
  return request.post(`/case/${caseId}/conversation/${conversationId}`)
}
