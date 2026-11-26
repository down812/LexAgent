<template>
  <div class="consult-page">
    <aside class="conv-sidebar">
      <div class="conv-header">
        <div class="conv-header-title">
          <el-icon><ChatLineSquare /></el-icon>
          <span>会话记录</span>
          <span class="conv-count" v-if="conversations.length">{{ conversations.length }}</span>
        </div>
        <el-button type="primary" size="small" round @click="newConversation">
          <el-icon><Plus /></el-icon>
          <span>新问诊</span>
        </el-button>
      </div>

      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="selectConversation(conv.id)"
        >
          <div class="conv-info">
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-time">{{ formatShortTime(conv.updatedAt || conv.createdAt) }}</div>
          </div>
          <el-icon class="conv-del" @click.stop="removeConversation(conv.id)"><Delete /></el-icon>
        </div>
        <div v-if="!conversations.length" class="conv-empty">
          <el-icon :size="32"><ChatDotRound /></el-icon>
          <p>暂无会话记录</p>
          <span>点击「新问诊」开始第一次咨询</span>
        </div>
      </div>
    </aside>

    <main class="chat-area">
      <!-- V2.0 案件上下文条（CHAT-001/11.6）：案件模式展示上下文，普通模式可选案件 -->
      <div class="case-context-bar">
        <template v-if="caseInfo">
          <el-icon><Folder /></el-icon>
          <span class="cc-name">{{ caseInfo.caseName }}</span>
          <el-tag size="small" effect="plain" type="info">{{ caseInfo.caseType }}</el-tag>
          <span class="cc-no">{{ caseInfo.caseNo }}</span>
          <span class="cc-stats">
            事实 {{ caseInfo.factCount || 0 }} 条 · 证据 {{ caseInfo.evidenceCount || 0 }} 份 · 问诊 {{ caseInfo.conversationCount || 0 }} 次
          </span>
          <span class="cc-hint">案件上下文已注入：事实、证据与问诊记录将自动参与本次分析</span>
          <el-button link size="small" @click="clearCaseContext">退出案件模式</el-button>
        </template>
        <template v-else>
          <el-icon><Folder /></el-icon>
          <span class="cc-hint">普通问诊（不关联案件）；选择案件后，系统将自动带上案件事实与证据上下文：</span>
          <el-select
            v-model="selectedCaseId"
            placeholder="选择案件进入案件问诊"
            size="small"
            style="width: 240px"
            filterable
            @change="onCaseSelect"
          >
            <el-option
              v-for="c in myCases"
              :key="c.id"
              :label="`${c.caseName}（${c.caseNo}）`"
              :value="c.id"
            />
          </el-select>
        </template>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <!-- 欢迎空状态 -->
        <div v-if="!messages.length && !streaming" class="chat-welcome">
          <div class="welcome-badge">
            <svg viewBox="0 0 32 32" width="44" height="44">
              <rect width="32" height="32" rx="8" fill="#d4a857" />
              <path d="M9 22V10h3.2v9.2h5.4V22H9zm9.8 0V10H22v12h-3.2z" fill="#16324f" />
            </svg>
          </div>
          <h2>您好，我是 LexAgent</h2>
          <p class="welcome-sub">多智能体协作的法律援助助手，为您解答法律问题、提供真实法条依据</p>
          <div class="welcome-examples">
            <div class="example-label">您可以这样提问</div>
            <div class="example-chip">我在公司工作两年，公司突然辞退我，没有提前通知，也没有补偿</div>
            <div class="example-chip">租房合同没到期，房东要涨租金赶我走，合法吗？</div>
            <div class="example-chip">借给朋友五万元没有借条，只有转账记录，能要回来吗？</div>
          </div>
        </div>

        <div v-for="msg in messages" :key="msg.id" class="msg-row" :class="msg.role">
          <!-- 用户消息：右侧浅色气泡（DeepSeek 风格） -->
          <template v-if="msg.role === 'user'">
            <div class="msg-avatar"><el-avatar :size="34">我</el-avatar></div>
            <div class="msg-content">
              <div class="msg-body"><div class="user-text">{{ msg.content }}</div></div>
            </div>
          </template>
          <!-- AI 消息：头像+名字独立一行，内容全宽无卡片排版 -->
          <template v-else>
            <div class="msg-avatar"><el-avatar :size="34">AI</el-avatar></div>
            <div class="msg-content">
              <div class="msg-role">LexAgent</div>
              <LegalAnswer v-if="msg.parsedAnswer" :answer="msg.parsedAnswer" :case-id="caseId" :message-id="msg.id" />
              <div v-else class="markdown-body fallback-md" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </template>
        </div>

        <div v-if="streaming" class="msg-row assistant">
          <div class="msg-avatar"><el-avatar :size="34">AI</el-avatar></div>
          <div class="msg-content">
            <div class="msg-role">LexAgent</div>
            <AgentProgress :events="sseEvents" :finished="streamFinished" />
            <div v-if="citations.length" class="citation-preview">
              <div class="cit-title">
                <el-icon><Reading /></el-icon> 检索到的法律依据
              </div>
              <el-tag v-for="(c, i) in citations" :key="i" type="info" effect="plain">
                {{ c.lawName }} {{ c.articleNo }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <div class="ocr-zone" v-if="ocrVisible">
          <el-upload
            :auto-upload="true"
            :http-request="handleOcrUpload"
            :show-file-list="false"
            accept=".jpg,.jpeg,.png,.bmp,.webp,.pdf"
            multiple
          >
            <el-button size="small" :loading="ocrLoading">
              <el-icon><Upload /></el-icon> 上传图片/PDF
            </el-button>
          </el-upload>

          <div v-if="ocrResults.length" class="ocr-results">
            <div v-for="(r, i) in ocrResults" :key="i" class="ocr-item">
              <div class="ocr-head">
                <el-icon><Document /></el-icon>
                <span class="ocr-name">{{ r.fileName }}</span>
                <el-tag size="small" :type="r.success ? 'success' : 'danger'">
                  {{ r.success ? `${r.charCount} 字` : '失败' }}
                </el-tag>
                <el-button text size="small" @click="appendOcrText(r)">填入</el-button>
              </div>
              <el-input v-if="r.success" v-model="r.text" type="textarea" :rows="3" readonly />
              <div v-else class="ocr-error">{{ r.errorMsg }}</div>
            </div>
          </div>
          <el-button text size="small" @click="ocrVisible = false">收起</el-button>
        </div>

        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="请描述您的法律问题…（Ctrl + Enter 发送）"
            resize="none"
            :disabled="streaming"
            @keydown.ctrl.enter="sendQuery"
          />
          <div class="input-bottom">
            <el-tooltip content="上传图片/PDF识别文字提问" placement="top">
              <el-button text class="ocr-toggle" @click="ocrVisible = !ocrVisible">
                <el-icon><Picture /></el-icon>
                <span>文档识别</span>
              </el-button>
            </el-tooltip>
            <el-button
              type="primary"
              :loading="streaming"
              :disabled="!inputText.trim()"
              @click="sendQuery"
              class="send-btn"
            >
              {{ streaming ? '协作处理中' : '发送' }}
            </el-button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import {
  Plus,
  Delete,
  ChatLineSquare,
  ChatDotRound,
  Reading,
  Upload,
  Picture,
  Document,
  Folder
} from '@element-plus/icons-vue'
import {
  listConversations,
  listMessages,
  deleteConversation,
  ocrRecognize
} from '@/api/chat'
import { getCase, queryCases } from '@/api/case'
import { fetchSSE } from '@/utils/sse'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import LegalAnswer from '@/components/LegalAnswer.vue'
import AgentProgress from '@/components/AgentProgress.vue'

const conversations = ref([])
const currentConversationId = ref(null) // null=未选择/全新会话；新会话由前端生成 UUID
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const streamFinished = ref(false)
const sseEvents = ref([])
const citations = ref([])
const sseController = ref(null)
const messagesRef = ref(null)

const ocrVisible = ref(false)
const ocrLoading = ref(false)
const ocrResults = ref([])

// ===== V2.0 案件上下文（CHAT-001/11.6 当前案件选择）=====
const route = useRoute()
const caseId = ref(null)
const caseInfo = ref(null)
const selectedCaseId = ref(null)
const myCases = ref([])

onMounted(() => {
  // 从案件详情"发起案件问诊"跳转进入时，注入案件上下文
  const qc = Number(route.query.caseId)
  if (qc > 0) {
    caseId.value = qc
    selectedCaseId.value = qc
    loadCaseContext(qc)
  }
  loadConversations()
  loadMyCases()
})

async function loadCaseContext(id) {
  try {
    const res = await getCase(id)
    if (res.code === 1) caseInfo.value = res.data
  } catch {
    // 无权访问或不存在时静默退出案件模式
    caseId.value = null
    caseInfo.value = null
    selectedCaseId.value = null
  }
}

async function loadMyCases() {
  try {
    const res = await queryCases({ pageNum: 1, pageSize: 100 }, { status: 'ACTIVE' })
    if (res.code === 1) myCases.value = res.data?.rows || []
  } catch {
    // 忽略
  }
}

function onCaseSelect(id) {
  if (id) {
    caseId.value = id
    loadCaseContext(id)
  }
}

function clearCaseContext() {
  caseId.value = null
  caseInfo.value = null
  selectedCaseId.value = null
}

async function loadConversations() {
  try {
    const res = await listConversations()
    if (res.code === 1) {
      conversations.value = res.data || []
      if (conversations.value.length && !currentConversationId.value) {
        selectConversation(conversations.value[0].id)
      }
    }
  } catch {
    // 忽略
  }
}

/**
 * 生成会话 ID（后端契约：ChatRequestDTO.conversationId 由前端生成，@NotBlank 必填）
 * crypto.randomUUID 需安全上下文（localhost/https 满足），否则用降级实现
 */
function generateConversationId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function newConversation() {
  // 新会话立即生成 UUID，避免首条消息发送时 conversationId 为空被后端 @NotBlank 拦截
  currentConversationId.value = generateConversationId()
  messages.value = []
  inputText.value = ''
}

async function selectConversation(id) {
  currentConversationId.value = id
  try {
    const res = await listMessages(id)
    if (res.code === 1) {
      messages.value = (res.data || []).map((m) => {
        const msg = { ...m }
        if (m.role === 'assistant') {
          // 后端契约：ChatMessageVO.content 为 Object——assistant 消息已被
          // MessageServiceImpl 解析为 FinalLegalAnswer 结构化对象（无需 JSON.parse）；
          // 仅降级场景（后端解析失败）content 才是字符串
          msg.parsedAnswer = normalizeAnswer(m.content)
        }
        return msg
      })
      scrollToBottom()
    }
  } catch {
    // 忽略
  }
}

/** 归一化 assistant content：对象直接用，字符串走容错解析 */
function normalizeAnswer(content) {
  if (content && typeof content === 'object') {
    return isAnswerLike(content) ? content : null
  }
  return tryParseAnswer(content)
}

/** 宽松校验：命中任一已知字段即视为结构化回答 */
function isAnswerLike(obj) {
  const KNOWN = ['preliminaryJudgment', 'legalBasis', 'caseAnalysis', 'suggestions', 'riskWarning', 'confidenceScore', 'reviewPassed']
  return KNOWN.some((k) => obj[k] != null && obj[k] !== '')
}

/**
 * 容错解析 assistant 消息中的 FinalLegalAnswer JSON
 * 兼容：```json 代码围栏、双重转义、JSON 前后夹杂文本、字段部分缺失
 */
function tryParseAnswer(content) {
  if (!content || typeof content !== 'string') return null
  let text = content.trim()

  // 1. 去掉 markdown 代码围栏（```json ... ``` / ``` ... ```）
  const fenceMatch = text.match(/```(?:json)?\s*([\s\S]*?)```/)
  if (fenceMatch) text = fenceMatch[1].trim()

  // 2. 尝试直接解析；失败则截取第一个 { 到最后一个 } 的 JSON 子串再解析
  let obj = tryJsonParse(text)
  if (obj == null) {
    const start = text.indexOf('{')
    const end = text.lastIndexOf('}')
    if (start !== -1 && end > start) obj = tryJsonParse(text.slice(start, end + 1))
  }

  // 3. 双重转义：解析结果是字符串则再解析一次
  if (typeof obj === 'string') obj = tryJsonParse(obj)

  // 4. 宽松校验
  if (obj && typeof obj === 'object' && isAnswerLike(obj)) return obj
  return null
}

function tryJsonParse(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/** 兜底渲染：非结构化内容按 Markdown 渲染，绝不裸露 JSON 原文 */
function renderMarkdown(content) {
  if (!content) return ''
  return DOMPurify.sanitize(marked.parse(String(content)))
}

async function removeConversation(id) {
  try {
    await ElMessageBox.confirm('确定删除此会话？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteConversation(id)
    ElMessage.success('已删除')
    if (id === currentConversationId.value) newConversation()
    loadConversations()
  } catch {
    // 忽略
  }
}

async function sendQuery() {
  const query = inputText.value.trim()
  if (!query || streaming.value) return

  // 发送前确保有会话 ID：覆盖"初始空态直接提问"的入口（未点过「新问诊」）
  if (!currentConversationId.value) {
    currentConversationId.value = generateConversationId()
  }

  messages.value.push({ id: Date.now(), role: 'user', content: query })
  inputText.value = ''

  streaming.value = true
  streamFinished.value = false
  sseEvents.value = []
  citations.value = []

  await scrollToBottom()

  sseController.value = fetchSSE(
    '/chat/consult',
    {
      conversationId: currentConversationId.value,
      query,
      caseId: caseId.value || undefined
    },
    {
      onEvent(obj, raw) {
        handleSSEEvent(obj, raw)
      },
      onError(err) {
        streaming.value = false
        ElMessage.error('请求失败：' + (err.message || '网络异常'))
      },
      onClose() {
        streaming.value = false
      }
    }
  )
}

function handleSSEEvent(obj, raw) {
  const { event } = raw

  if (event === SSE_EVENT.AGENT_START || event === SSE_EVENT.AGENT_RESULT || event === SSE_EVENT.ERROR) {
    sseEvents.value.push(obj)
  }

  if (event === SSE_EVENT.CITATION) {
    citations.value = obj.data || []
  }

  if (event === SSE_EVENT.CONTENT) {
    const answer = obj.data
    if (answer) {
      messages.value.push({
        id: Date.now(),
        role: 'assistant',
        content: JSON.stringify(answer),
        parsedAnswer: answer
      })
      citations.value = []
    }
  }

  if (event === SSE_EVENT.DONE) {
    streamFinished.value = true
    streaming.value = false
    // V2.0（11.6 评价入口）：done 事件携带 assistant 真实消息ID，回填到最后一条回答
    const realId = obj.data?.assistantMessageId
    if (realId) {
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') {
          messages.value[i].id = realId
          break
        }
      }
    }
    loadConversations()
  }

  if (event === SSE_EVENT.ERROR) {
    streamFinished.value = true
    streaming.value = false
    ElMessage.error(obj.message || '处理失败')
  }

  scrollToBottom()
}

function scrollToBottom() {
  return nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function handleOcrUpload(option) {
  ocrLoading.value = true
  try {
    const res = await ocrRecognize([option.file])
    if (res.code === 1 && res.data) {
      ocrResults.value.push(...res.data)
    }
  } catch {
    // 已处理
  } finally {
    ocrLoading.value = false
  }
}

function appendOcrText(r) {
  if (r.text) {
    inputText.value = inputText.value ? `${inputText.value}\n${r.text}` : r.text
    ElMessage.success('已填入')
  }
}
</script>

<style scoped>
.consult-page {
  display: flex;
  height: 100%;
  overflow: hidden;
}
/* ============ V2.0 案件上下文条 ============ */
.case-context-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--color-primary-bg);
  border-bottom: 1px solid var(--color-border-light);
  font-size: 13px;
  color: var(--color-text);
  flex-shrink: 0;
}
.case-context-bar .el-icon {
  color: var(--color-accent-dark);
}
.cc-name {
  font-weight: 600;
  color: var(--color-primary);
}
.cc-no {
  font-size: 12px;
  color: var(--color-text-muted);
}
.cc-stats {
  font-size: 12px;
  color: var(--color-primary-light);
  white-space: nowrap;
}
.cc-hint {
  flex: 1;
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* ============ 会话侧栏 ============ */
.conv-sidebar {
  width: 264px;
  background: #fff;
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 14px;
  border-bottom: 1px solid var(--color-border-light);
}
.conv-header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}
.conv-header-title .el-icon {
  color: var(--color-accent-dark);
}
.conv-count {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
  border-radius: 8px;
  padding: 1px 7px;
  margin-left: 2px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}
.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  margin-bottom: 2px;
}
.conv-item:hover {
  background: var(--color-bg);
}
.conv-item.active {
  background: var(--color-primary-bg);
  border-color: rgba(26, 58, 92, 0.1);
}
.conv-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 2px;
  background: var(--color-accent);
}
.conv-info {
  flex: 1;
  overflow: hidden;
}
.conv-title {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-item.active .conv-title {
  color: var(--color-primary);
  font-weight: 600;
}
.conv-time {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 3px;
}
.conv-del {
  opacity: 0;
  color: var(--color-danger);
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.conv-item:hover .conv-del {
  opacity: 0.6;
}
.conv-del:hover {
  opacity: 1 !important;
}
.conv-empty {
  text-align: center;
  padding: 48px 16px;
  color: var(--color-text-muted);
}
.conv-empty .el-icon {
  color: #cbd5e1;
  margin-bottom: 10px;
}
.conv-empty p {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
.conv-empty span {
  font-size: 12px;
}

/* ============ 聊天区 ============ */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
}
/* 欢迎空状态 */
.chat-welcome {
  max-width: 640px;
  margin: 6vh auto 0;
  text-align: center;
}
.welcome-badge {
  display: inline-flex;
  padding: 14px;
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--shadow);
  margin-bottom: 20px;
}
.chat-welcome h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
}
.welcome-sub {
  margin-top: 10px;
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.8;
}
.welcome-examples {
  margin-top: 32px;
  text-align: left;
}
.example-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 10px;
  text-align: center;
  letter-spacing: 0.1em;
}
.example-chip {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 13px 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  margin-bottom: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.example-chip:hover {
  border-color: var(--color-accent);
  box-shadow: var(--shadow);
}
/* 消息 */
.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 26px;
  max-width: 960px;
}
.msg-row.user {
  flex-direction: row-reverse;
  margin-left: auto;
}
.msg-avatar {
  flex-shrink: 0;
}
.msg-row.user .msg-avatar .el-avatar {
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-dark) 100%);
  font-size: 13px;
}
.msg-row.assistant .msg-avatar .el-avatar {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  color: var(--color-accent-light);
  font-size: 13px;
  font-weight: 600;
}
.msg-content {
  flex: 1;
  min-width: 0;
}
.msg-row.user .msg-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex: 0 1 auto;
}
.msg-role {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}
.msg-body {
  background: #eef2f7;
  border-radius: 16px 16px 4px 16px;
  padding: 12px 18px;
  max-width: 640px;
  box-shadow: var(--shadow-sm);
}
.user-text {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--color-text);
}
/* AI 消息内容区：无卡片，全宽排版 */
.msg-row.assistant .msg-content {
  padding-top: 2px;
}
.fallback-md {
  color: var(--color-text);
}
.citation-preview {
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}
.cit-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.citation-preview .el-tag {
  margin: 2px 4px 2px 0;
}

/* ============ 输入区 ============ */
.chat-input {
  border-top: 1px solid var(--color-border-light);
  background: #fff;
  padding: 14px 24px 16px;
}
.ocr-zone {
  margin-bottom: 10px;
  padding: 12px 14px;
  background: var(--color-bg);
  border-radius: 10px;
}
.ocr-results {
  margin: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ocr-item {
  background: #fff;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.ocr-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
}
.ocr-name {
  flex: 1;
  font-weight: 500;
}
.ocr-error {
  color: var(--color-danger);
  font-size: 13px;
}
.input-box {
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: 10px 12px 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
}
.input-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(26, 58, 92, 0.06);
}
.input-box :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none;
  padding: 4px 6px;
  font-size: 14px;
  line-height: 1.7;
  background: transparent;
}
.input-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.ocr-toggle {
  color: var(--color-text-secondary);
  font-size: 13px;
  gap: 4px;
}
.ocr-toggle:hover {
  color: var(--color-primary);
}
.send-btn {
  min-width: 96px;
  height: 38px;
  border-radius: 10px;
  font-size: 14px;
}
</style>
