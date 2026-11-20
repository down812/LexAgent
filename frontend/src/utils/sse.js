import { useUserStore } from '@/stores/user'

/**
 * 基于 fetch + ReadableStream 的 SSE 客户端
 *
 * 后端 /chat/consult 返回 text/event-stream，事件格式：
 *   event:agent_start
 *   data:{"event":"agent_start","agentType":"CASE_ANALYSIS",...}
 *   id:1234567890
 *
 * 因为需要 POST body + 自定义 token header，不能用 EventSource（仅支持 GET）。
 *
 * @param {string} url     请求地址（不含 baseURL 前缀）
 * @param {object} body    请求体
 * @param {object} handlers  { onEvent(eventObj, rawEvent), onError(err), onClose() }
 * @returns {AbortController}  调用 .abort() 可中断
 */
export function fetchSSE(url, body, { onEvent, onError, onClose } = {}) {
  const controller = new AbortController()
  const userStore = useUserStore()
  const baseURL = import.meta.env.VITE_API_BASE || '/api'

  ;(async () => {
    try {
      const resp = await fetch(`${baseURL}${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          token: userStore.token || ''
        },
        body: JSON.stringify(body),
        signal: controller.signal
      })

      if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
      }

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // SSE 事件以空行分隔（兼容 \n\n 和 \r\n\r\n）
        while (true) {
          const idxNl = buffer.indexOf('\n\n')
          const idxCrlf = buffer.indexOf('\r\n\r\n')
          let sepIndex = -1
          let sepLen = 0

          if (idxNl !== -1 && (idxCrlf === -1 || idxNl < idxCrlf)) {
            sepIndex = idxNl
            sepLen = 2
          } else if (idxCrlf !== -1) {
            sepIndex = idxCrlf
            sepLen = 4
          } else {
            break
          }

          const block = buffer.slice(0, sepIndex)
          buffer = buffer.slice(sepIndex + sepLen)

          const parsed = parseSSEBlock(block)
          if (parsed) {
            onEvent && onEvent(parsed.obj, parsed)
          }
        }
      }

      // 处理残留 buffer
      if (buffer.trim()) {
        const parsed = parseSSEBlock(buffer)
        if (parsed) onEvent && onEvent(parsed.obj, parsed)
      }

      onClose && onClose()
    } catch (err) {
      if (err.name === 'AbortError') {
        onClose && onClose()
        return
      }
      onError && onError(err)
    }
  })()

  return controller
}

/**
 * 解析单个 SSE 事件块
 * 返回 { event, data, id, obj }
 */
function parseSSEBlock(block) {
  const lines = block.split(/\r?\n/)
  let event = 'message'
  let data = ''
  let id = ''

  for (const line of lines) {
    if (!line) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    // 去掉冒号后的一个前导空格（SSE 规范）
    let val = colon === -1 ? '' : line.slice(colon + 1)
    if (val.startsWith(' ')) val = val.slice(1)

    if (field === 'event') event = val
    else if (field === 'data') data += (data ? '\n' : '') + val
    else if (field === 'id') id = val
  }

  if (!data) return null

  // 尝试解析 JSON
  let obj = null
  try {
    obj = JSON.parse(data)
  } catch {
    obj = { message: data }
  }

  return { event, data, id, obj }
}
