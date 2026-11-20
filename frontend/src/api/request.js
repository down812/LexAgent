import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

// 请求拦截：注入 token header
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['token'] = userStore.token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一拆 Result 结构
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 { code, msg, data }，code=1 成功
    if (res.code !== undefined) {
      if (res.code === 1) {
        return res
      }
      // 业务失败
      ElMessage.error(res.msg || '请求失败')
      // token 失效相关提示
      if (res.msg && (res.msg.includes('token') || res.msg.includes('登录'))) {
        const userStore = useUserStore()
        userStore.clearLoginInfo()
        router.push('/login')
      }
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    // 非 Result 结构（如直接返回数据）
    return { code: 1, data: res, msg: '' }
  },
  (error) => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      const userStore = useUserStore()
      userStore.clearLoginInfo()
      ElMessage.error('登录已过期，请重新登录')
      router.push('/login')
    } else if (status >= 500) {
      ElMessage.error('服务器异常，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
