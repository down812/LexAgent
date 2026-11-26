import request from './request'

// ===== 回答评价（V2.0 11.6 评价入口 / EVAL-001/002） =====

/** 提交评价（重复提交视为修改） */
export function submitFeedback(data) {
  return request.post('/feedback', data)
}

/** 修改评价 */
export function updateFeedback(id, data) {
  return request.put(`/feedback/${id}`, data)
}

/** 查询本人对某条回答的评价（无评价返回 data=null） */
export function getMyFeedback(messageId) {
  return request.get('/feedback/my', { params: { messageId } })
}

// ===== 管理员统计（11.7） =====

/** 分页查询反馈（管理员） */
export function queryFeedbackPage(params, data) {
  return request.post('/feedback/page', data, { params })
}

/** 评价统计（管理员）：总数/好评率/差评原因分布/近14天趋势 */
export function getFeedbackStatistics() {
  return request.get('/feedback/statistics')
}
