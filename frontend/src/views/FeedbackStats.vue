<template>
  <div class="page-container feedback-stats-page">
    <div class="page-header">
      <div class="page-header-icon"><el-icon><DataAnalysis /></el-icon></div>
      <div class="page-header-text">
        <h2>评价统计</h2>
        <p>用户反馈总数、好评率、差评原因分布与回答质量变化趋势（管理员可见）</p>
      </div>
      <div class="toolbar-spacer" />
      <el-button @click="loadAll" :loading="statsLoading">刷新数据</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards" v-loading="statsLoading">
      <div class="stat-card">
        <div class="stat-value">{{ stats?.totalCount ?? 0 }}</div>
        <div class="stat-label">反馈总数</div>
      </div>
      <div class="stat-card highlight">
        <div class="stat-value">{{ stats?.goodRate ?? 0 }}%</div>
        <div class="stat-label">回答好评率</div>
        <el-progress
          :percentage="stats?.goodRate ?? 0"
          :stroke-width="6"
          :show-text="false"
          :color="goodRateColor"
          class="stat-progress"
        />
      </div>
      <div class="stat-card">
        <div class="stat-value good">{{ stats?.helpfulCount ?? 0 }}</div>
        <div class="stat-label">有帮助</div>
      </div>
      <div class="stat-card">
        <div class="stat-value bad">{{ stats?.notHelpfulCount ?? 0 }}</div>
        <div class="stat-label">没帮助</div>
      </div>
    </div>

    <div class="charts-row">
      <!-- 差评原因分布 -->
      <div class="card chart-card">
        <div class="chart-title">差评原因分布</div>
        <div v-if="stats?.reasonDistribution?.length" class="reason-list">
          <div v-for="r in stats.reasonDistribution" :key="r.reason" class="reason-row">
            <span class="reason-name">{{ r.reason }}</span>
            <div class="reason-bar-track">
              <div class="reason-bar" :style="{ width: Math.max(r.percent, 4) + '%' }"></div>
            </div>
            <span class="reason-count">{{ r.count }} 次（{{ r.percent }}%）</span>
          </div>
        </div>
        <el-empty v-else description="暂无差评反馈" :image-size="60" />
      </div>

      <!-- 近14天质量趋势（SVG 折线图：好评率 + 反馈量双轴） -->
      <div class="card chart-card">
        <div class="chart-title">近 14 天回答质量变化</div>
        <div v-if="stats?.trend?.length" class="trend-chart-wrap">
          <svg :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="trend-svg" preserveAspectRatio="xMidYMid meet">
            <!-- 网格与左轴刻度（好评率 0-100） -->
            <g v-for="v in gridValues" :key="'g' + v">
              <line
                :x1="PLOT_L" :x2="CHART_W - PLOT_R" :y1="rateY(v)" :y2="rateY(v)"
                stroke="var(--color-border-light)" stroke-width="1"
              />
              <text :x="PLOT_L - 8" :y="rateY(v) + 4" class="axis-text" text-anchor="end">{{ v }}</text>
            </g>
            <!-- 右轴刻度（反馈量） -->
            <text
              v-for="v in volumeTicks" :key="'r' + v"
              :x="CHART_W - PLOT_R + 8" :y="volumeY(v) + 4" class="axis-text" text-anchor="start"
            >{{ v }}</text>

            <!-- 反馈量柱状（辅助序列，右轴） -->
            <rect
              v-for="(p, i) in volumePoints" :key="'v' + i"
              :x="p.x - BAR_W / 2" :y="p.y"
              :width="BAR_W" :height="CHART_H - PLOT_B - p.y"
              fill="var(--color-accent-bg)" rx="2"
            >
              <title>{{ p.date }} 反馈 {{ p.value }} 条</title>
            </rect>

            <!-- 好评率折线（主序列，左轴；无反馈日按 0 显示） -->
            <path
              v-for="(seg, si) in rateSegments" :key="'seg' + si"
              :d="seg" fill="none"
              stroke="var(--color-primary)" stroke-width="2.5"
              stroke-linecap="round" stroke-linejoin="round"
            />
            <!-- 数据点 -->
            <circle
              v-for="(p, i) in ratePoints" :key="'p' + i"
              :cx="p.x" :cy="p.y" r="4"
              :fill="p.value == null ? 'var(--color-border)' : '#fff'"
              stroke="var(--color-primary)" stroke-width="2"
            >
              <title>{{ p.date }} {{ p.label }}（反馈 {{ p.volume }} 条）</title>
            </circle>

            <!-- 图例 -->
            <line :x1="PLOT_L" :x2="PLOT_L + 20" :y1="CHART_H - 12" :y2="CHART_H - 12" stroke="var(--color-primary)" stroke-width="2.5" />
            <text :x="PLOT_L + 26" :y="CHART_H - 8" class="legend-text">好评率%</text>
            <rect :x="PLOT_L + 90" :y="CHART_H - 17" width="12" height="10" fill="var(--color-accent-bg)" rx="2" />
            <text :x="PLOT_L + 108" :y="CHART_H - 8" class="legend-text">反馈量</text>

            <!-- X 轴日期（隔天显示避免拥挤） -->
            <text
              v-for="(p, i) in volumePoints" :key="'x' + i"
              :x="p.x" :y="CHART_H - PLOT_B + 16"
              class="axis-text" :text-anchor="i === 0 ? 'start' : i === volumePoints.length - 1 ? 'end' : 'middle'"
            >{{ i % 2 === 0 || i === volumePoints.length - 1 ? p.date.slice(5) : '' }}</text>
          </svg>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 反馈明细列表 -->
    <div class="card">
      <div class="toolbar" style="margin-bottom: 14px">
        <el-select v-model="filters.rating" placeholder="评价结果" clearable style="width: 140px" @change="searchList">
          <el-option label="有帮助" :value="1" />
          <el-option label="没帮助" :value="0" />
        </el-select>
        <el-input
          v-model="filters.reason"
          placeholder="按差评原因查询"
          clearable
          style="width: 200px"
          @keyup.enter="searchList"
        />
        <el-button type="primary" @click="searchList">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="rating" label="评价" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.rating === 1 ? 'success' : 'danger'" size="small">
              {{ row.rating === 1 ? '有帮助' : '没帮助' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" width="140">
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="content" label="文字反馈" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column prop="answerExcerpt" label="回答摘要" min-width="260" show-overflow-tooltip />
        <el-table-column prop="messageId" label="消息ID" width="100" />
        <el-table-column prop="createdAt" label="评价时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无反馈数据" /></template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  queryFeedbackPage,
  getFeedbackStatistics
} from '@/api/feedback'

const stats = ref(null)
const statsLoading = ref(false)

const filters = reactive({ rating: null, reason: '' })
const { tableData, loading, pageNum, pageSize, total, search, refresh } = useTable(queryFeedbackPage, {
  pageSize: 10
})

const maxDailyTotal = computed(() => {
  const arr = stats.value?.trend || []
  return Math.max(1, ...arr.map((t) => t.total))
})

const goodRateColor = computed(() => {
  const s = stats.value?.goodRate ?? 0
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
})

// ===== SVG 折线图（好评率 + 反馈量双轴，零依赖） =====
const CHART_W = 720
const CHART_H = 260
const PLOT_L = 40
const PLOT_R = 56
const PLOT_T = 18
const PLOT_B = 42
const BAR_W = 14

const trendData = computed(() => stats.value?.trend || [])

/** 左轴网格刻度：0/25/50/75/100 */
const gridValues = [0, 25, 50, 75, 100]

/** 右轴反馈量刻度：0 到向上取整的 max */
const volumeTicks = computed(() => {
  const max = maxDailyTotal.value
  const step = Math.max(1, Math.ceil(max / 4))
  const ticks = []
  for (let v = 0; v <= max + step * 0.5; v += step) ticks.push(v)
  return ticks
})

/** x 坐标：14 个点在绘图区内均匀分布 */
function xAt(i) {
  const n = trendData.value.length
  if (n <= 1) return PLOT_L
  return PLOT_L + ((CHART_W - PLOT_L - PLOT_R) * i) / (n - 1)
}
/** 好评率 y：0-100 映射到绘图区高度 */
function rateY(v) {
  return PLOT_T + (1 - v / 100) * (CHART_H - PLOT_T - PLOT_B)
}
/** 反馈量 y：按右轴最大值映射 */
function volumeY(v) {
  return PLOT_T + (1 - v / (maxDailyTotal.value + 1)) * (CHART_H - PLOT_T - PLOT_B)
}

/** 反馈量柱状坐标 */
const volumePoints = computed(() =>
  trendData.value.map((t, i) => ({
    x: xAt(i),
    y: volumeY(t.total),
    date: t.date,
    value: t.total
  }))
)

/** 好评率折线：无反馈日按 0 显示（用户要求：0 也画出，不留空白） */
const rateSegments = computed(() => {
  if (trendData.value.length === 0) return []
  const cmds = trendData.value.map((t, i) => {
    const v = t.goodRate ?? 0 // 无反馈日按 0 计入折线
    return `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)},${rateY(v).toFixed(1)}`
  })
  return [cmds.join(' ')]
})

/** 好评率数据点（用于画圆点，全部显示；无反馈日 title 标注说明） */
const ratePoints = computed(() =>
  trendData.value.map((t, i) => ({
    x: xAt(i),
    y: rateY(t.goodRate ?? 0),
    date: t.date,
    value: t.goodRate,
    volume: t.total,
    label: t.goodRate == null ? `无反馈，按 0 计` : `好评率 ${t.goodRate}%`
  }))
)

function searchList() {
  search({
    rating: filters.rating ?? undefined,
    reason: filters.reason || undefined
  })
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getFeedbackStatistics()
    if (res.code === 1) stats.value = res.data
  } catch {
    // 错误已在拦截器提示
  } finally {
    statsLoading.value = false
  }
}

function loadAll() {
  loadStats()
  searchList()
}

onMounted(loadAll)
</script>

<style scoped>
.feedback-stats-page {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
}
.toolbar-spacer {
  flex: 1;
}
/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 18px 20px;
  text-align: center;
}
.stat-card.highlight {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
}
.stat-card.highlight .stat-value,
.stat-card.highlight .stat-label {
  color: #fff;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.3;
}
.stat-value.good {
  color: var(--color-success);
}
.stat-value.bad {
  color: var(--color-danger);
}
.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.stat-card.highlight .stat-progress {
  margin-top: 8px;
}
/* 图表区 */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 16px;
  margin-bottom: 16px;
}
.chart-card {
  padding: 16px 20px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 14px;
}
.reason-row,
.trend-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.reason-name {
  width: 110px;
  font-size: 13px;
  color: var(--color-text);
  text-align: right;
  flex-shrink: 0;
}
.reason-bar-track,
.trend-bar-track {
  flex: 1;
  height: 12px;
  background: var(--color-bg);
  border-radius: 6px;
  overflow: hidden;
}
.reason-bar {
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, #f56c6c 0%, #f89898 100%);
  transition: width 0.4s;
}
.trend-chart-wrap {
  width: 100%;
}
.trend-svg {
  width: 100%;
  height: auto;
  display: block;
}
.axis-text {
  font-size: 11px;
  fill: var(--color-text-muted);
}
.legend-text {
  font-size: 12px;
  fill: var(--color-text-secondary);
}
.card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 16px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
@media (max-width: 900px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
