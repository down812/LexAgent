import request from './request'

// 发送邮箱验证码
export function sendEmailCode(email) {
  return request.post('/user/email/code', null, { params: { email } })
}

// ===== 当前登录用户信息 =====

// 查询用户信息
export function getUserInfo() {
  return request.get('/user/info')
}

// 更新用户信息（identifier/password/email/phone 均可选，留空不修改）
export function updateUserInfo(data) {
  return request.put('/user/info', data)
}

// 创建子账号
export function createSubaccount(data) {
  return request.post('/user/subaccount', data)
}

// 分页查询子账号
export function querySubaccounts(params, data) {
  return request.post('/user/subaccountList', data, { params })
}

// 修改子账号
export function updateSubaccount(id, data) {
  return request.put(`/user/subaccount/${id}`, data)
}

// 删除子账号
export function deleteSubaccount(id) {
  return request.delete(`/user/subaccount/${id}`)
}
