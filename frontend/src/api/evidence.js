import request from './request'

// ===== 案件证据管理（V2.0） =====

/** 上传证据（自动异步触发 OCR/解析） */
export function uploadEvidence(params) {
  const formData = new FormData()
  formData.append('file', params.file)
  if (params.evidenceName) formData.append('evidenceName', params.evidenceName)
  if (params.category) formData.append('category', params.category)
  if (params.remark) formData.append('remark', params.remark)
  return request.post('/evidence/upload', formData, {
    params: { caseId: params.caseId },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 分页查询证据列表 */
export function queryEvidences(params, data) {
  return request.post('/evidence/page', data, { params })
}

/** 查询证据详情（含 OCR 文本与修正文本） */
export function getEvidence(id) {
  return request.get(`/evidence/${id}`)
}

/** 修改证据信息（名称/分类/备注/修正文本） */
export function updateEvidence(data) {
  return request.put('/evidence/update', data)
}

/** 删除证据 */
export function deleteEvidence(id) {
  return request.delete(`/evidence/${id}`)
}

/** 重新 OCR 识别 */
export function retryOcr(id) {
  return request.post(`/evidence/${id}/ocr`)
}

/** 获取 OCR 识别文本 */
export function getOcrText(id) {
  return request.get(`/evidence/${id}/ocr`)
}

// ===== 智能证据分析（V2.0 阶段三） =====

/** 触发证据智能分析（异步） */
export function analyzeEvidence(id) {
  return request.post(`/evidence/${id}/analyze`, null, { timeout: 30000 })
}

/** 查询证据最新分析结果 */
export function getEvidenceAnalysis(id) {
  return request.get(`/evidence/${id}/analysis`)
}

/** 确认/取消证据与案件事实关联 */
export function updateRelation(id, factId, confirmed) {
  return request.put(`/evidence/${id}/analysis/relation`, { factId, confirmed })
}

/** 案件证据完整性分析（同步，LLM 耗时较长） */
export function analyzeIntegrity(caseId) {
  return request.post(`/evidence/case/${caseId}/integrity`, null, { timeout: 120000 })
}
