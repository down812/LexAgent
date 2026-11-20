<!--
  ═══════════════════════════════════════════════════════════
  脚手架页面模板 — 新增功能页时复制此文件改名即可
  ═══════════════════════════════════════════════════════════

  脚手架约定（务必遵守）：

  1. <script setup> 中无需手动导入以下内容（auto-import 自动注入）：
     - Vue API：ref / reactive / computed / watch / onMounted / nextTick …
     - Vue Router API：useRoute / useRouter / createRouter / createWebHistory …
     - Pinia API：defineStore / storeToRefs …
     - Element Plus 函数：ElMessage / ElMessageBox / ElNotification …
     - src/composables 下的所有 composable（useTable / useRequest / useDialog …）
     - src/constants 下的所有常量（USER_TYPE / FILE_STATUS / …）
     - src/stores 下的所有 store（useUserStore …）

  2. 仍需手动导入的：
     - API 请求函数：import { xxxApi } from '@/api/xxx'
     - Element Plus 图标：import { Search, Plus } from '@element-plus/icons-vue'
     - 非 components 目录的自定义组件（LegalAnswer / AgentProgress）
     - 第三方库：import { marked } from 'marked'

  3. Element Plus 组件 <el-xxx> 无需 import，unplugin-vue-components 自动注册
     src/components 下的组件也自动注册（如 <LegalAnswer /> 可直接用）

  4. 路径别名 @ → src/，全程使用 @ 引用

  5. 页面放进 src/views/，组件放进 src/components/，
     composable 放进 src/composables/，API 放进 src/api/

  ═══════════════════════════════════════════════════════════
-->

<template>
  <div class="page-container">
    <div class="page-title">
      <el-icon><Document /></el-icon>
      页面标题
    </div>

    <div class="card">
      <!-- 查询区 -->
      <div class="toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="名称">
            <el-input v-model="query.keyword" placeholder="关键字" clearable @keyup.enter="searchData" />
          </el-form-item>
          <el-button type="primary" @click="searchData">查询</el-button>
        </el-form>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="warning" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
// ===== 仅需导入 API 函数和图标（其余全部 auto-import） =====
import { listYourData, deleteYourData } from '@/api/yourModule'
import { Document } from '@element-plus/icons-vue'

// ===== 响应式状态 =====
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const query = reactive({ keyword: '' })

// ===== 数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const res = await listYourData(
      { pageNum: pageNum.value, pageSize: pageSize.value },
      { ...query }
    )
    if (res.code === 1) {
      tableData.value = res.data?.rows || []
      total.value = res.data?.total || 0
    }
  } catch {
    // axios 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

function searchData() {
  pageNum.value = 1
  loadData()
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteYourData(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ===== 初始化 =====
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
