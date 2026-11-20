/**
 * 通用格式化工具函数
 */

/** 时间 → 'YYYY-MM-DD HH:mm' */
export function formatDateTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 时间 → 'MM-DD HH:mm'（会话列表用） */
export function formatShortTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

/** 文件大小 → B / KB / MB */
export function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

/** 0~1 分数 → 百分比整数 */
export function toPercent(score) {
  if (score == null) return 0
  return Math.round(score * 100)
}
