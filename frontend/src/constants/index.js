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

// ===== 证据 OCR 处理状态（后端复用 WAITING/PROCESSING/SUCCESS/FAILED） =====
export const OCR_STATUS_LABELS = {
  WAITING: '等待处理',
  PROCESSING: '识别中',
  SUCCESS: '识别成功',
  FAILED: '识别失败'
}

export const OCR_STATUS_TAG = {
  WAITING: 'info',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger'
}

// ===== 证据智能分析状态（V2.0 阶段三） =====
export const ANALYSIS_STATUS_LABELS = {
  PROCESSING: '分析中',
  SUCCESS: '分析完成',
  FAILED: '分析失败'
}

export const ANALYSIS_STATUS_TAG = {
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger'
}

// ===== 证据清单状态（V2.0 阶段四） =====
export const CHECKLIST_STATUS = {
  REQUIRED: 'REQUIRED',
  UPLOADED: 'UPLOADED',
  CONFIRMED: 'CONFIRMED',
  OPTIONAL: 'OPTIONAL'
}

export const CHECKLIST_STATUS_LABELS = {
  [CHECKLIST_STATUS.REQUIRED]: '建议收集',
  [CHECKLIST_STATUS.UPLOADED]: '已上传',
  [CHECKLIST_STATUS.CONFIRMED]: '用户已确认',
  [CHECKLIST_STATUS.OPTIONAL]: '可选材料'
}

export const CHECKLIST_STATUS_TAG = {
  [CHECKLIST_STATUS.REQUIRED]: 'warning',
  [CHECKLIST_STATUS.UPLOADED]: 'primary',
  [CHECKLIST_STATUS.CONFIRMED]: 'success',
  [CHECKLIST_STATUS.OPTIONAL]: 'info'
}

export const CHECKLIST_STATUS_OPTIONS = Object.values(CHECKLIST_STATUS).map((v) => ({
  label: CHECKLIST_STATUS_LABELS[v],
  value: v
}))

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
  REVIEWER: 'REVIEWER',
  EVIDENCE_ANALYSIS: 'EVIDENCE_ANALYSIS'
}

export const AGENT_NAME = {
  [AGENT_TYPE.CASE_ANALYSIS]: '案件分析 Agent',
  [AGENT_TYPE.LEGAL_SEARCH]: '法律检索 Agent',
  [AGENT_TYPE.LEGAL_ANALYSIS]: '法律分析 Agent',
  [AGENT_TYPE.REVIEWER]: '核验审核 Agent',
  [AGENT_TYPE.EVIDENCE_ANALYSIS]: '证据分析 Agent'
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

// ===== 案件状态（V2.0） =====
export const CASE_STATUS = {
  ACTIVE: 'ACTIVE',
  PENDING: 'PENDING',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED'
}

export const CASE_STATUS_LABELS = {
  [CASE_STATUS.ACTIVE]: '进行中',
  [CASE_STATUS.PENDING]: '待补充信息',
  [CASE_STATUS.COMPLETED]: '已完成',
  [CASE_STATUS.ARCHIVED]: '已归档'
}

export const CASE_STATUS_TAG = {
  [CASE_STATUS.ACTIVE]: 'primary',
  [CASE_STATUS.PENDING]: 'warning',
  [CASE_STATUS.COMPLETED]: 'success',
  [CASE_STATUS.ARCHIVED]: 'info'
}

export const CASE_STATUS_OPTIONS = Object.entries(CASE_STATUS).map(([_, value]) => ({
  label: CASE_STATUS_LABELS[value],
  value
}))

// ===== 案件类型（需求文档 3.2 建议） =====
export const CASE_TYPES = [
  '劳动争议',
  '合同纠纷',
  '消费者权益纠纷',
  '借贷纠纷',
  '房屋租赁纠纷',
  '婚姻家庭纠纷',
  '人身损害纠纷',
  '其他民事纠纷'
]

// ===== 证据分类（需求文档 4.3 建议） =====
export const EVIDENCE_CATEGORIES = [
  '身份证明',
  '合同协议',
  '财务凭证',
  '沟通记录',
  '书面材料',
  '影像资料',
  '其他材料'
]

// ===== 案件事实来源 =====
export const FACT_SOURCE_LABELS = {
  USER_INPUT: '用户输入',
  OCR_EXTRACTION: 'OCR提取',
  AI_ANALYSIS: 'AI分析',
  USER_CORRECTION: '用户修正'
}

// ===== 证据文件类型 =====
export const EVIDENCE_ACCEPT = '.jpg,.jpeg,.png,.bmp,.webp,.pdf,.doc,.docx,.txt'
export const EVIDENCE_MAX_SIZE_MB = 20

export function formatDateTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
