/**
 * 全局常量集中管理
 * 后端枚举与前端展示保持同步
 */

// ===== 用户类型（后端 UserEnum） =====
export const USER_TYPE = {
  SUPER_ADMIN: 0,
  ADMIN: 1,
  USER: 2
}

export const USER_TYPE_LABELS = {
  [USER_TYPE.SUPER_ADMIN]: '超级管理员',
  [USER_TYPE.ADMIN]: '普通管理员',
  [USER_TYPE.USER]: '普通用户'
}

export const USER_TYPE_TAG = {
  [USER_TYPE.SUPER_ADMIN]: 'danger',
  [USER_TYPE.ADMIN]: 'warning',
  [USER_TYPE.USER]: 'info'
}

/** 是否管理员（含超管） */
export function isAdmin(type) {
  return type === USER_TYPE.SUPER_ADMIN || type === USER_TYPE.ADMIN
}

/** 是否超管 */
export function isSuperAdmin(type) {
  return type === USER_TYPE.SUPER_ADMIN
}

// ===== 文件处理状态（后端 FileStatusEnum） =====
export const FILE_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED'
}

export const FILE_STATUS_LABELS = {
  [FILE_STATUS.PENDING]: '待处理',
  [FILE_STATUS.PROCESSING]: '处理中',
  [FILE_STATUS.SUCCESS]: '已完成',
  [FILE_STATUS.FAILED]: '失败'
}

export const FILE_STATUS_TAG = {
  [FILE_STATUS.PENDING]: 'info',
  [FILE_STATUS.PROCESSING]: 'warning',
  [FILE_STATUS.SUCCESS]: 'success',
  [FILE_STATUS.FAILED]: 'danger'
}

// ===== 法律领域 =====
export const LEGAL_DOMAINS = [
  { label: '劳动法', value: '劳动法' },
  { label: '消费者权益', value: '消费者权益' },
  { label: '民事纠纷', value: '民事纠纷' },
  { label: '其他', value: '其他' }
]

// ===== SSE Agent 类型 =====
export const AGENT_TYPE = {
  CASE_ANALYSIS: 'CASE_ANALYSIS',
  LEGAL_SEARCH: 'LEGAL_SEARCH',
  LEGAL_ANALYSIS: 'LEGAL_ANALYSIS',
  REVIEWER: 'REVIEWER'
}

export const AGENT_NAME = {
  [AGENT_TYPE.CASE_ANALYSIS]: '案件分析 Agent',
  [AGENT_TYPE.LEGAL_SEARCH]: '法律检索 Agent',
  [AGENT_TYPE.LEGAL_ANALYSIS]: '法律分析 Agent',
  [AGENT_TYPE.REVIEWER]: '核验审核 Agent'
}

// ===== SSE 事件类型 =====
export const SSE_EVENT = {
  AGENT_START: 'agent_start',
  AGENT_RESULT: 'agent_result',
  CITATION: 'citation',
  CONTENT: 'content',
  DONE: 'done',
  ERROR: 'error'
}

// ===== 混合检索命中类型 =====
export const MATCH_TYPE_LABELS = {
  VECTOR: '仅向量',
  KEYWORD: '仅关键词',
  HYBRID: '向量+关键词'
}

export const MATCH_TYPE_TAG = {
  VECTOR: 'primary',
  KEYWORD: 'success',
  HYBRID: 'warning'
}
