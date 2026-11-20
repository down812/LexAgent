<template>
  <div class="page-container search-page">
    <div class="page-header">
      <div class="page-header-icon"><el-icon><Search /></el-icon></div>
      <div class="page-header-text">
        <h2>知识检索测试</h2>
        <p>对比基础向量检索与混合检索（RRF 融合 + 多特征重排），验证召回质量与条款真实性校验</p>
      </div>
    </div>

    <div class="card search-box">
      <el-input
        v-model="query"
        type="textarea"
        :rows="2"
        placeholder="输入法律问题进行检索，例如：公司辞退员工不支付补偿怎么办？"
        resize="none"
      />
      <div class="search-controls">
        <el-select v-model="datasetId" placeholder="知识库（可选）" clearable style="width: 190px">
          <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-input-number v-model="topK" :min="1" :max="20" label="TopK" />
        <el-radio-group v-model="searchType">
          <el-radio-button value="basic">基础向量检索</el-radio-button>
          <el-radio-button value="hybrid">混合检索（Rerank）</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="loading" @click="doSearch">
          <el-icon><Search /></el-icon> 检索
        </el-button>
      </div>
    </div>

    <div v-if="hybridStats" class="stats-row">
      <div class="stat-card">
        <span class="stat-value">{{ hybridStats.total }}</span>
        <span class="stat-label">召回总数</span>
      </div>
      <div class="stat-card verified-card">
        <span class="stat-value verified">{{ hybridStats.verified }}</span>
        <span class="stat-label">已校验</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ hybridStats.vectorCount }}</span>
        <span class="stat-label">向量命中</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ hybridStats.keywordCount }}</span>
        <span class="stat-label">关键词命中</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ hybridStats.hybridCount }}</span>
        <span class="stat-label">混合命中</span>
      </div>
    </div>

    <div class="results-container" v-loading="loading">
      <div v-for="(item, i) in results" :key="i" class="result-card" :class="{ verified: item.verified }">
        <div class="result-header">
          <span class="result-rank">{{ i + 1 }}</span>
          <span class="result-law">{{ item.lawName }}</span>
          <el-tag size="small" effect="plain">{{ item.articleNo }}</el-tag>
          <el-tag v-if="item.chapter" size="small" type="info" effect="plain">{{ item.chapter }}</el-tag>
          <el-tag v-if="item.domain" size="small" type="warning" effect="plain">{{ item.domain }}</el-tag>

          <span class="result-tags-right" v-if="searchType === 'hybrid'">
            <el-tag :type="MATCH_TYPE_TAG[item.matchType]" size="small" effect="plain">
              {{ MATCH_TYPE_LABELS[item.matchType] }}
            </el-tag>
            <el-tag v-if="item.verified === true" type="success" size="small">
              <el-icon><CircleCheck /></el-icon> 已校验
            </el-tag>
            <el-tag v-else-if="item.verified === false" type="danger" size="small">
              <el-icon><CircleClose /></el-icon> 未校验
            </el-tag>
          </span>
        </div>

        <div class="result-content">{{ item.content }}</div>

        <div class="score-bar" v-if="searchType === 'hybrid'">
          <div class="score-item">
            <span class="score-label">向量</span>
            <el-progress :percentage="toPercent(item.vectorScore)" :stroke-width="6" color="#409eff" />
          </div>
          <div class="score-item">
            <span class="score-label">关键词</span>
            <el-progress :percentage="toPercent(item.keywordScore)" :stroke-width="6" color="#67c23a" />
          </div>
          <div class="score-item">
            <span class="score-label">RRF</span>
            <el-progress :percentage="toPercent(item.rrfScore)" :stroke-width="6" color="#e6a23c" />
          </div>
          <div class="score-item rerank">
            <span class="score-label">Rerank</span>
            <el-progress :percentage="toPercent(item.rerankScore)" :stroke-width="6" color="#d4a857" />
          </div>
        </div>

        <div class="score-bar single" v-else>
          <div class="score-item">
            <span class="score-label">相似度</span>
            <el-progress :percentage="toPercent(item.score)" :stroke-width="6" color="#409eff" />
          </div>
        </div>

        <div v-if="item.verifyDetail" class="verify-detail">
          <el-icon><InfoFilled /></el-icon> {{ item.verifyDetail }}
        </div>
      </div>

      <div v-if="!loading && !results.length" class="results-empty">
        <el-icon :size="40"><Search /></el-icon>
        <p>输入法律问题，点击「检索」查看召回结果</p>
        <span>推荐使用「混合检索」体验 RRF 融合与多特征重排效果</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import { basicSearch, hybridSearch, queryDatasets } from '@/api/knowledge'

const query = ref('')
const datasetId = ref(null)
const topK = ref(5)
const searchType = ref('hybrid')
const loading = ref(false)
const results = ref([])
const datasets = ref([])

const hybridStats = computed(() => {
  if (searchType.value !== 'hybrid' || !results.value.length) return null
  const r = results.value
  return {
    total: r.length,
    verified: r.filter((x) => x.verified === true).length,
    vectorCount: r.filter((x) => x.matchType === 'VECTOR').length,
    keywordCount: r.filter((x) => x.matchType === 'KEYWORD').length,
    hybridCount: r.filter((x) => x.matchType === 'HYBRID').length
  }
})

async function doSearch() {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  loading.value = true
  results.value = []
  try {
    const params = {
      query: query.value,
      datasetId: datasetId.value || undefined,
      topK: topK.value
    }
    const fn = searchType.value === 'hybrid' ? hybridSearch : basicSearch
    const res = await fn(params)
    if (res.code === 1) {
      results.value = res.data || []
    }
  } catch {
    // 忽略
  } finally {
    loading.value = false
  }
}

async function loadDatasets() {
  try {
    const res = await queryDatasets({ pageNum: 1, pageSize: 100 }, {})
    if (res.code === 1) {
      datasets.value = res.data?.rows || []
    }
  } catch {
    // 忽略
  }
}

onMounted(() => {
  loadDatasets()
})
</script>

<style scoped>
.search-page {
  height: 100%;
  overflow-y: auto;
}
.search-box {
  margin-bottom: 18px;
}
.search-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  flex-wrap: wrap;
}
.search-controls > .el-button {
  margin-left: auto;
}
/* 统计卡片 */
.stats-row {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  flex: 1;
  background: #fff;
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: var(--shadow-sm);
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-card.verified-card {
  border-color: rgba(103, 194, 58, 0.25);
  background: linear-gradient(180deg, rgba(103, 194, 58, 0.04) 0%, #fff 100%);
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.2;
}
.stat-value.verified {
  color: var(--color-success);
}
/* 结果卡片 */
.results-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.result-card {
  background: #fff;
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  padding: 18px 22px;
  box-shadow: var(--shadow-sm);
  border-left: 3px solid transparent;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.result-card:hover {
  box-shadow: var(--shadow-hover);
}
.result-card.verified {
  border-left-color: var(--color-success);
}
.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.result-rank {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--color-accent-bg);
  color: var(--color-accent-dark);
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.result-law {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
  margin-right: 2px;
}
.result-tags-right {
  margin-left: auto;
  display: inline-flex;
  gap: 6px;
}
.result-content {
  font-size: 14px;
  line-height: 1.85;
  color: #475569;
  margin-bottom: 14px;
  white-space: pre-wrap;
}
.score-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  background: #fafbfc;
  border-radius: 10px;
  padding: 12px 16px;
}
.score-bar.single {
  grid-template-columns: 1fr;
  max-width: 420px;
}
.score-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  min-width: 36px;
}
.score-item .el-progress {
  flex: 1;
}
@media (max-width: 768px) {
  .score-bar {
    grid-template-columns: 1fr 1fr;
  }
}
.verify-detail {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 5px;
}
.results-empty {
  text-align: center;
  padding: 64px 20px;
  color: var(--color-text-muted);
}
.results-empty .el-icon {
  color: #cbd5e1;
  margin-bottom: 14px;
}
.results-empty p {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}
.results-empty span {
  font-size: 12px;
}
</style>
