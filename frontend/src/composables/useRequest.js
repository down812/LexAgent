/**
 * 通用异步请求 composable：自动管理 loading / error
 *
 * 用法：
 *   const { loading, run } = useRequest(api.listData)
 *   const data = await run(params)   // loading 自动 true→false
 *
 * @param {function} fn 返回 Promise 的函数
 */
export function useRequest(fn) {
  const loading = ref(false)
  const error = ref(null)

  async function run(...args) {
    loading.value = true
    error.value = null
    try {
      return await fn(...args)
    } catch (e) {
      error.value = e
      throw e
    } finally {
      loading.value = false
    }
  }

  return { loading, error, run }
}
