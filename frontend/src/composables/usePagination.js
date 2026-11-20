/**
 * 通用分页 composable
 *
 * 用法：
 *   const { pageNum, pageSize, total, reset } = usePagination()
 *   // 查询时自动带上 pageNum/pageSize
 *   watch([pageNum, pageSize], loadData)
 *
 * @param {object} init 初始值 { pageNum, pageSize }
 */
export function usePagination(init = {}) {
  const pageNum = ref(init.pageNum ?? 1)
  const pageSize = ref(init.pageSize ?? 20)
  const total = ref(0)

  function reset() {
    pageNum.value = 1
  }

  /** 把分页参数导出为对象，方便传给 API */
  function params() {
    return { pageNum: pageNum.value, pageSize: pageSize.value }
  }

  return { pageNum, pageSize, total, reset, params }
}
