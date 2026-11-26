<template>
  <div class="legal-answer">
    <!-- 审核可信度：轻量元信息行 -->
    <div class="answer-meta" v-if="answer.confidenceScore != null || answer.reviewPassed != null">
      <span class="meta-badge" :class="answer.reviewPassed ? 'pass' : 'warn'">
        <el-icon><CircleCheck v-if="answer.reviewPassed" /><WarningFilled v-else /></el-icon>
        {{ answer.reviewPassed ? '审核通过' : '需人工复核' }}
      </span>
      <template v-if="answer.confidenceScore != null">
        <span class="meta-score-label">可信度</span>
        <el-progress :percentage="answer.confidenceScore" :color="confidenceColor" :stroke-width="6" class="meta-score-bar" :show-text="false" />
        <span class="meta-score-value">{{ answer.confidenceScore }}</span>
      </template>
    </div>

    <section v-if="answer.preliminaryJudgment">
      <h3><span class="section-icon"><el-icon><Document /></el-icon></span>初步判断</h3>
      <p class="judgment-text">{{ answer.preliminaryJudgment }}</p>
    </section>

    <section v-if="answer.legalBasis?.length">
      <h3><span class="section-icon"><el-icon><Reading /></el-icon></span>法律依据</h3>
      <div v-for="(item, i) in answer.legalBasis" :key="i" class="basis-card">
        <div class="basis-head">
          <span class="basis-law">{{ item.lawName }}</span>
          <el-tag type="info" effect="plain" size="small">{{ item.articleNo }}</el-tag>
        </div>
        <p class="basis-content">{{ item.content }}</p>
        <p class="basis-relevance" v-if="item.relevance">
          <el-icon><Connection /></el-icon>
          <span>{{ item.relevance }}</span>
        </p>
      </div>
    </section>

    <section v-if="answer.caseAnalysis">
      <h3><span class="section-icon"><el-icon><View /></el-icon></span>结合你的情况</h3>
      <div class="markdown-body" v-html="renderedAnalysis"></div>
    </section>

    <section v-if="answer.suggestions?.length">
      <h3><span class="section-icon"><el-icon><Promotion /></el-icon></span>处理建议</h3>
      <ol class="suggestion-list">
        <li v-for="(s, i) in answer.suggestions" :key="i">{{ s }}</li>
      </ol>
    </section>

    <section v-if="answer.evidenceReferences?.length">
      <h3><span class="section-icon"><el-icon><FolderChecked /></el-icon></span>证据引用</h3>
      <p class="missing-hint">以下发现来自您案件中的证据材料（证据分析 Agent 提取）：</p>
      <div v-for="(ev, i) in answer.evidenceReferences" :key="i" class="evidence-ref-item">
        <el-icon><Link /></el-icon>
        <span>{{ ev }}</span>
      </div>
    </section>

    <section v-if="answer.missingInfo?.length">
      <h3><span class="section-icon"><el-icon><QuestionFilled /></el-icon></span>需要您补充的信息</h3>
      <p class="missing-hint">以下信息会影响分析的准确性，您可以点击补充，系统会将其记录为案件事实：</p>
      <div v-for="(m, i) in answer.missingInfo" :key="i" class="missing-item">
        <span class="missing-text">{{ m }}</span>
        <el-button
          v-if="caseId"
          size="small"
          type="primary"
          plain
          :disabled="addedFacts[i]"
          :loading="addingIndex === i"
          @click="addFact(m, i)"
        >{{ addedFacts[i] ? '已补充' : '补充为案件事实' }}</el-button>
      </div>
    </section>

    <section v-if="answer.riskWarning" class="risk-section">
      <h3><span class="section-icon warning"><el-icon><WarningFilled /></el-icon></span>风险提示</h3>
      <el-alert type="warning" :closable="false" show-icon>
        {{ answer.riskWarning }}
      </el-alert>
    </section>

    <div class="answer-disclaimer">本回答由 AI 生成，仅供参考，不构成正式法律意见。重大事项请咨询执业律师。</div>

    <!-- 回答评价入口（V2.0 11.6） -->
    <div v-if="messageId" class="feedback-bar">
      <span class="feedback-label">这次回答对您有帮助吗？</span>
      <template v-if="!feedbackDone">
        <el-button size="small" :type="ratingDraft === 1 ? 'primary' : 'default'" plain @click="rate(1)">
          <el-icon><Top /></el-icon>有帮助
        </el-button>
        <el-button size="small" :type="ratingDraft === 0 ? 'danger' : 'default'" plain @click="rate(0)">
          <el-icon><Bottom /></el-icon>没帮助
        </el-button>
      </template>
      <template v-else>
        <el-tag :type="myRating === 1 ? 'success' : 'warning'" size="small" effect="plain">
          已评价：{{ myRating === 1 ? '有帮助' : '没帮助' }}
        </el-tag>
        <el-button link size="small" @click="rate(myRating)">修改评价</el-button>
      </template>
    </div>

    <!-- 评价原因对话框 -->
    <el-dialog v-model="feedbackVisible" title="回答评价" width="440" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="评价结果">
          <el-radio-group v-model="ratingDraft">
            <el-radio :value="1">有帮助</el-radio>
            <el-radio :value="0">没帮助</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="ratingDraft === 0" label="原因">
          <el-select v-model="reasonDraft" placeholder="选择原因（可选）" clearable style="width: 100%">
            <el-option v-for="r in FEEDBACK_REASONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="文字反馈">
          <el-input
            v-model="contentDraft"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="补充说明（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="doSubmitFeedback">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { ElMessage } from 'element-plus'
import { addCaseFact } from '@/api/case'
import { submitFeedback, getMyFeedback } from '@/api/feedback'

const props = defineProps({
  answer: { type: Object, required: true },
  caseId: { type: Number, default: null }, // V2.0：传入案件ID后支持一键补充事实（CHAT-002）
  messageId: { type: Number, default: null } // V2.0：回答消息ID，用于评价入口（11.6）
})

// ===== 评价状态 =====
const FEEDBACK_REASONS = ['法律依据不准确', '内容不完整', '表达不清晰', '解决不了问题', '其他']
const ratingDraft = ref(1)
const reasonDraft = ref('')
const contentDraft = ref('')
const feedbackVisible = ref(false)
const feedbackSubmitting = ref(false)
const myRating = ref(null) // 已提交的评价结果
const feedbackDone = computed(() => myRating.value !== null)

// 有 messageId 时回显历史评价
watch(
  () => props.messageId,
  async (mid) => {
    myRating.value = null
    if (!mid) return
    try {
      const res = await getMyFeedback(mid)
      if (res.code === 1 && res.data) {
        myRating.value = res.data.rating
        ratingDraft.value = res.data.rating
        reasonDraft.value = res.data.reason || ''
        contentDraft.value = res.data.content || ''
      }
    } catch {
      // 忽略
    }
  },
  { immediate: true }
)

function rate(value) {
  ratingDraft.value = value
  reasonDraft.value = ''
  feedbackVisible.value = true
}

async function doSubmitFeedback() {
  feedbackSubmitting.value = true
  try {
    const res = await submitFeedback({
      messageId: props.messageId,
      rating: ratingDraft.value,
      reason: reasonDraft.value || undefined,
      content: contentDraft.value || undefined
    })
    if (res.code === 1) {
      ElMessage.success('感谢您的反馈')
      myRating.value = ratingDraft.value
      feedbackVisible.value = false
    }
  } catch {
    // 错误已在拦截器提示
  } finally {
    feedbackSubmitting.value = false
  }
}

const addedFacts = reactive({})
const addingIndex = ref(-1)

async function addFact(text, index) {
  if (!props.caseId) return
  addingIndex.value = index
  try {
    const res = await addCaseFact(props.caseId, text)
    if (res.code === 1) {
      ElMessage.success('已补充为案件事实')
      addedFacts[index] = true
    }
  } catch {
    // 错误已在拦截器提示
  } finally {
    addingIndex.value = -1
  }
}

const confidenceColor = computed(() => {
  const s = props.answer.confidenceScore
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
})

const renderedAnalysis = computed(() => {
  if (!props.answer.caseAnalysis) return ''
  const html = marked.parse(props.answer.caseAnalysis)
  return DOMPurify.sanitize(html)
})
</script>

<style scoped>
.legal-answer {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
/* 轻量元信息行 */
.answer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.meta-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 20px;
  font-weight: 500;
}
.meta-badge.pass {
  color: var(--color-success);
  background: rgba(103, 194, 58, 0.1);
}
.meta-badge.warn {
  color: var(--color-warning);
  background: rgba(230, 162, 60, 0.12);
}
.meta-score-label {
  margin-left: 4px;
}
.meta-score-bar {
  width: 120px;
}
.meta-score-value {
  font-weight: 600;
  color: var(--color-primary);
}
/* 章节 */
section h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 14px;
}
.section-icon.warning {
  background: rgba(230, 162, 60, 0.12);
  color: var(--color-warning);
}
section p {
  font-size: 14px;
  line-height: 1.85;
}
.judgment-text {
  color: var(--color-text);
  background: #fafbfc;
  border-radius: 10px;
  padding: 12px 16px;
}
/* 法律依据卡片 */
.basis-card {
  background: #fafbfc;
  border-left: 3px solid var(--color-accent);
  border-radius: 0 10px 10px 0;
  padding: 14px 18px;
  margin-bottom: 10px;
}
.basis-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.basis-law {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-primary);
}
.basis-content {
  font-size: 13px;
  color: #475569;
  line-height: 1.8;
  margin: 4px 0;
}
.basis-relevance {
  font-size: 12px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
  line-height: 1.7;
}
.basis-relevance .el-icon {
  margin-top: 4px;
  flex-shrink: 0;
  color: var(--color-accent-dark);
}
/* 建议列表 */
.suggestion-list {
  padding-left: 0;
  list-style: none;
  counter-reset: suggestion;
}
.suggestion-list li {
  counter-increment: suggestion;
  position: relative;
  padding-left: 34px;
  font-size: 14px;
  line-height: 1.85;
  margin-bottom: 10px;
}
.suggestion-list li::before {
  content: counter(suggestion);
  position: absolute;
  left: 0;
  top: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.risk-section :deep(.el-alert) {
  align-items: flex-start;
  border-radius: 10px;
}
.missing-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}
.missing-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #fafbfc;
  border-radius: 10px;
  padding: 10px 16px;
  margin-bottom: 8px;
}
.missing-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text);
}
.evidence-ref-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: var(--color-primary-bg);
  border-radius: 10px;
  padding: 10px 16px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text);
}
.evidence-ref-item .el-icon {
  margin-top: 4px;
  flex-shrink: 0;
  color: var(--color-primary-light);
}
.feedback-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fafbfc;
  border-radius: 10px;
}
.feedback-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.answer-disclaimer {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
  line-height: 1.7;
}
</style>
