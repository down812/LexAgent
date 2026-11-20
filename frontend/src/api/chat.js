import request from './request'

// 会话列表
export function listConversations() {
  return request.get('/chat/conversations')
}

// 会话历史消息
export function listMessages(conversationId) {
  return request.get(`/chat/messages/${conversationId}`)
}

// 删除会话（归档）
export function deleteConversation(conversationId) {
  return request.delete(`/chat/conversation/${conversationId}`)
}

// 图片/PDF OCR 识别
export function ocrRecognize(files) {
  const formData = new FormData()
  files.forEach((f) => formData.append('files', f))
  return request.post('/chat/ocr', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}
