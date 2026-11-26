<template>
  <div class="page">
    <!-- 搜索与筛选 -->
    <div class="toolbar">
      <el-input
        v-model="filters.caseName"
        placeholder="按案件名称查询"
        clearable
        class="filter-input"
        @keyup.enter="doSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filters.caseType" placeholder="案件类型" clearable class="filter-select">
        <el-option v-for="t in CASE_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="filters.status" placeholder="案件状态" clearable class="filter-select">
        <el-option v-for="opt in CASE_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" @click="doSearch">查询</el-button>
      <div class="toolbar-spacer" />
      <el-button type="primary" @click="openCreate">
        <el-icon class="btn-icon"><Plus /></el-icon>创建案件
      </el-button>
    </div>

    <!-- 案件列表 -->
    <div class="card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="caseNo" label="案件编号" width="180" show-overflow-tooltip />
        <el-table-column prop="caseName" label="案件名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="caseType" label="案件类型" width="140" />
        <el-table-column prop="status" label="案件状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="CASE_STATUS_TAG[row.status] || 'info'" effect="plain">
              {{ CASE_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="warning"
              :disabled="row.status === CASE_STATUS.ARCHIVED"
              @click="doArchive(row)"
            >归档</el-button>
            <el-button link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无案件，点击右上角创建" />
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </div>

    <!-- 创建/编辑案件弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editId ? '编辑案件' : '创建案件'"
      width="560"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="案件名称" prop="caseName">
          <el-input v-model="form.caseName" placeholder="自定义案件名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="案件类型" prop="caseType">
          <el-select v-model="form.caseType" placeholder="选择案件类型" style="width: 100%">
            <el-option v-for="t in CASE_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="案件描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="案件基本情况描述"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="form.occurredAt"
            type="datetime"
            placeholder="案件发生时间（选填）"
            style="width: 100%"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="发生地点">
          <el-input v-model="form.location" placeholder="案件发生地点（选填）" />
        </el-form-item>
        <el-form-item label="涉及主体">
          <el-input v-model="form.partiesInvolved" placeholder="涉及的公司、个人等（选填）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="补充信息（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {
  createCase,
  updateCase,
  archiveCase,
  deleteCase,
  queryCases
} from '@/api/case'

const router = useRouter()
const { tableData, loading, pageNum, pageSize, total, search, refresh } = useTable(queryCases, {
  pageSize: 10
})

// 筛选条件：默认"进行中"，归档案件需主动筛选查看
const filters = reactive({
  caseName: '',
  caseType: '',
  status: CASE_STATUS.ACTIVE
})

function buildQuery() {
  return {
    caseName: filters.caseName || undefined,
    caseType: filters.caseType || undefined,
    status: filters.status || undefined
  }
}

function doSearch() {
  search(buildQuery())
}

onMounted(() => doSearch())

// ===== 创建 / 编辑 =====
const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({
  caseName: '',
  caseType: '',
  description: '',
  occurredAt: null,
  location: '',
  partiesInvolved: '',
  remark: ''
})

const rules = {
  caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
  caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入案件描述', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, {
    caseName: '',
    caseType: '',
    description: '',
    occurredAt: null,
    location: '',
    partiesInvolved: '',
    remark: ''
  })
}

function openCreate() {
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editId.value = row.id
  Object.assign(form, {
    caseName: row.caseName,
    caseType: row.caseType,
    description: row.description || '',
    occurredAt: row.occurredAt,
    location: row.location || '',
    partiesInvolved: row.partiesInvolved || '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (editId.value) {
        const res = await updateCase({ id: editId.value, ...form })
        if (res.code === 1) ElMessage.success('案件已更新')
      } else {
        const res = await createCase(form)
        if (res.code === 1) {
          ElMessage.success(`案件创建成功，编号：${res.data?.caseNo || ''}`)
          dialogVisible.value = false
          router.push(`/cases/${res.data.id}`)
          return
        }
      }
      dialogVisible.value = false
      await refresh()
    } catch {
      // 错误已在拦截器提示
    } finally {
      saving.value = false
    }
  })
}

// ===== 归档 / 删除 =====
async function doArchive(row) {
  try {
    await ElMessageBox.confirm(
      `确定将案件「${row.caseName}」归档吗？归档后历史问诊记录与证据材料全部保留。`,
      '归档案件',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const res = await archiveCase(row.id)
    if (res.code === 1) {
      ElMessage.success('案件已归档')
      await refresh()
    }
  } catch {
    // 错误已在拦截器提示
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除案件「${row.caseName}」吗？该操作将级联删除案件事实、问诊关联与全部证据材料，不可恢复！`,
      '删除案件',
      { type: 'error', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    const res = await deleteCase(row.id)
    if (res.code === 1) {
      ElMessage.success('案件已删除')
      await refresh()
    }
  } catch {
    // 错误已在拦截器提示
  }
}

function goDetail(row) {
  router.push(`/cases/${row.id}`)
}
</script>

<style scoped>
.page {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.filter-input {
  width: 240px;
}
.filter-select {
  width: 150px;
}
.toolbar-spacer {
  flex: 1;
}
.btn-icon {
  margin-right: 4px;
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
</style>
