<template>
  <div class="agent-progress">
    <div class="progress-title">
      <span class="status-dot" :class="finished ? 'done' : 'running'">
        <el-icon v-if="!finished" class="rotating"><Loading /></el-icon>
        <el-icon v-else><CircleCheck /></el-icon>
      </span>
      <span class="title-text">{{ finished ? '多 Agent 协作完成' : '多 Agent 正在协作处理' }}</span>
      <span v-if="!finished" class="title-ellipsis">…</span>
    </div>

    <div class="agent-list">
      <div
        v-for="(agent, index) in agents"
        :key="agent.type"
        class="agent-item"
        :class="agent.status"
      >
        <div class="agent-track">
          <div class="agent-dot">
            <el-icon v-if="agent.status === 'done'"><Check /></el-icon>
            <el-icon v-else-if="agent.status === 'running'" class="rotating"><Loading /></el-icon>
            <span v-else class="dot-number">{{ index + 1 }}</span>
          </div>
          <div v-if="index < agents.length - 1" class="agent-line" :class="{ filled: agent.status === 'done' }"></div>
        </div>
        <div class="agent-body">
          <div class="agent-name">{{ agent.name }}</div>
          <div class="agent-msg" v-if="agent.message">{{ agent.message }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  events: { type: Array, default: () => [] },
  finished: { type: Boolean, default: false }
})

const agents = ref([
  { type: AGENT_TYPE.CASE_ANALYSIS, name: AGENT_NAME[AGENT_TYPE.CASE_ANALYSIS], status: 'pending', message: '' },
  { type: AGENT_TYPE.LEGAL_SEARCH, name: AGENT_NAME[AGENT_TYPE.LEGAL_SEARCH], status: 'pending', message: '' },
  { type: AGENT_TYPE.LEGAL_ANALYSIS, name: AGENT_NAME[AGENT_TYPE.LEGAL_ANALYSIS], status: 'pending', message: '' },
  { type: AGENT_TYPE.REVIEWER, name: AGENT_NAME[AGENT_TYPE.REVIEWER], status: 'pending', message: '' }
])

watch(
  () => props.events,
  (evts) => {
    for (const evt of evts) {
      const idx = agents.value.findIndex((a) => a.type === evt.agentType)
      if (idx === -1) continue
      const agent = agents.value[idx]
      if (evt.event === SSE_EVENT.AGENT_START) {
        agent.status = 'running'
        agent.message = evt.message || '处理中…'
      } else if (evt.event === SSE_EVENT.AGENT_RESULT) {
        agent.status = 'done'
        agent.message = evt.message || '已完成'
      }
    }
  },
  { deep: true, immediate: true }
)

watch(
  () => props.finished,
  (done) => {
    if (done) {
      agents.value.forEach((a) => {
        if (a.status === 'running') a.status = 'done'
      })
    }
  }
)
</script>

<style scoped>
.agent-progress {
  background: linear-gradient(135deg, #f8fafc 0%, #f4f6fa 100%);
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  padding: 16px 20px;
}
.progress-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 16px;
}
.status-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 13px;
}
.status-dot.running {
  color: var(--color-accent-dark);
  background: var(--color-accent-bg);
}
.status-dot.done {
  color: var(--color-success);
  background: rgba(103, 194, 58, 0.12);
}
.title-ellipsis {
  animation: blink 1.2s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.2; }
}
.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.agent-list {
  display: flex;
  flex-direction: column;
}
.agent-item {
  display: flex;
  align-items: stretch;
  gap: 12px;
}
.agent-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 24px;
}
.agent-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
  background: #e2e8f0;
  color: var(--color-text-muted);
  transition: background 0.2s, color 0.2s;
}
.dot-number {
  font-size: 12px;
  font-weight: 600;
}
.agent-item.running .agent-dot {
  background: var(--color-accent);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(212, 168, 87, 0.18);
}
.agent-item.done .agent-dot {
  background: var(--color-success);
  color: #fff;
}
.agent-line {
  flex: 1;
  width: 2px;
  min-height: 14px;
  background: #e2e8f0;
  margin: 3px 0;
  border-radius: 1px;
}
.agent-line.filled {
  background: rgba(103, 194, 58, 0.45);
}
.agent-body {
  padding-bottom: 16px;
  padding-top: 2px;
}
.agent-item:last-child .agent-body {
  padding-bottom: 2px;
}
.agent-item.pending .agent-body {
  opacity: 0.55;
}
.agent-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.agent-item.running .agent-name {
  color: var(--color-accent-dark);
}
.agent-msg {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 3px;
  line-height: 1.6;
}
</style>
