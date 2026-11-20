import request from './request'

// 统一登录（identifier + password）
export function login(data) {
  return request.post('/auth/login', data)
}

// 注册
export function register(data) {
  return request.post('/auth/register', data)
}

// 退出登录
export function logout() {
  return request.post('/auth/logout')
}
