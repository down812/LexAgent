import request from './request'

// ===== 数据集管理 =====

export function createDataset(data) {
  return request.post('/knowledge/dataset', data)
}

export function queryDatasets(params, data) {
  return request.post('/knowledge/dataset/page', data, { params })
}

export function getDataset(id) {
  return request.get(`/knowledge/dataset/${id}`)
}

export function updateDataset(data) {
  return request.put('/knowledge/dataset', data)
}

export function deleteDataset(id) {
  return request.delete(`/knowledge/dataset/${id}`)
}

// ===== 文件管理 =====

export function uploadFile(datasetId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/file/upload', formData, {
    params: { datasetId },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function queryFiles(params, data) {
  return request.post('/knowledge/file/page', data, { params })
}

export function getFile(id) {
  return request.get(`/knowledge/file/${id}`)
}

export function deleteFile(id) {
  return request.delete(`/knowledge/file/${id}`)
}

// ===== 知识检索 =====

export function basicSearch(params) {
  return request.post('/knowledge/search', null, { params })
}

export function hybridSearch(params) {
  return request.post('/knowledge/search/hybrid', null, { params })
}

export function getOcrStatus() {
  return request.get('/knowledge/ocr/status')
}
