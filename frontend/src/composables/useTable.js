/**
 * 表格数据管理 composable：分页 + 查询 + loading 三合一
 *
 * 用法：
 *   const { tableData, loading, pageNum, pageSize, total, search, refresh }
 *     = useTable(api.queryFiles, { pageSize: 20 })
 *
 *   // 查询（重置到第一页）
 *   await search({ status: 'SUCCESS' })
 *
 *   // 翻页/改 pageSize 自动触发
 *   // 也可以手动 refresh() 刷新当前页
 *
 * @param {function} fetcher (params, query) => Promise<Result<PageResult>>  参数会收到 { pageNum, pageSize } + query
 * @param {object} opts { pageSize, immediate, dataKey } dataKey 默认 'rows'
 */
export function useTable(fetcher, opts = {}) {
  const { pageNum, pageSize, total, reset, params } = usePagination({
    pageSize: opts.pageSize ?? 20
  })

  const query = ref({})          // 当前查询条件
  const tableData = ref([])      // 表格数据
  const loading = ref(false)

  /** 读取 res.data 的列表字段（兼容 rows/list） */
  const dataKey = opts.dataKey ?? 'rows'

  async function load() {
    loading.value = true
    try {
      const res = await fetcher(params(), unref(query.value))
      if (res?.code === 1) {
        tableData.value = res.data?.[dataKey] || []
        total.value = res.data?.total || 0
      }
    } catch {
      // 错误已在 axios 拦截器提示
    } finally {
      loading.value = false
    }
  }

  /** 带条件查询（重置到第一页） */
  async function search(q = {}) {
    query.value = q
    reset()
    await load()
  }

  /** 刷新当前页（不重置分页） */
  async function refresh() {
    await load()
  }

  // 翻页/改 pageSize 自动重新加载
  watch([pageNum, pageSize], () => {
    if (tableData.value.length || total.value) load()
  })

  // 是否立即加载
  if (opts.immediate !== false) {
    onMounted(() => load())
  }

  return {
    tableData,
    loading,
    pageNum,
    pageSize,
    total,
    query,
    search,
    refresh,
    reset
  }
}
