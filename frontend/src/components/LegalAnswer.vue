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

    <section v-if="answer.riskWarning" class="risk-section">
      <h3><span class="section-icon warning"><el-icon><WarningFilled /></el-icon></span>风险提示</h3>
      <el-alert type="warning" :closable="false" show-icon>
        {{ answer.riskWarning }}
      </el-alert>
    </section>

    <div class="answer-disclaimer">本回答由 AI 生成，仅供参考，不构成正式法律意见。重大事项请咨询执业律师。</div>
  </div>
</template>

<script setup>
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  answer: { type: Object, required: true }
})

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
.answer-disclaimer {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
  line-height: 1.7;
}
</style>
