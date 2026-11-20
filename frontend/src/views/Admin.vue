<template>
  <div class="page-container admin-page">
    <div class="page-header">
      <div class="page-header-icon"><el-icon><UserFilled /></el-icon></div>
      <div class="page-header-text">
        <h2>子账号管理</h2>
        <p>创建与维护系统子账号，分配管理员与普通用户角色（仅超级管理员可见）</p>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="账号">
            <el-input v-model="query.identifier" placeholder="账号关键字" clearable style="width: 200px" @keyup.enter="searchList" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.type" placeholder="全部" clearable style="width: 150px">
              <el-option
                v-for="(label, key) in USER_TYPE_LABELS"
                :key="key"
                :label="label"
                :value="Number(key)"
              />
            </el-select>
          </el-form-item>
          <el-button type="primary" @click="searchList">查询</el-button>
          <el-button type="success" @click="openCreate">
            <el-icon><Plus /></el-icon> 创建子账号
          </el-button>
        </el-form>
      </div>

      <el-table :data="list" v-loading="loading">
        <el-table-column prop="subaccountId" label="ID" width="80" />
        <el-table-column prop="identifier" label="账号" min-width="160">
          <template #default="{ row }">
            <div class="account-cell">
              <el-avatar :size="28" class="account-avatar">{{ row.identifier?.charAt(0).toUpperCase() }}</el-avatar>
              <span class="account-name">{{ row.identifier }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="140">
          <template #default="{ row }">
            <el-tag :type="USER_TYPE_TAG[row.type]" effect="plain">{{ USER_TYPE_LABELS[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="warning" @click="openEdit(row)">修改</el-button>
            <el-button text type="danger" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 创建子账号 -->
    <el-dialog v-model="createDialogVisible" title="创建子账号" width="480">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
        <el-form-item label="账号" prop="identifier">
          <el-input v-model="createForm.identifier" placeholder="6-20位字母或数字" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="8-20位，含大小写字母和数字" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="createForm.type" placeholder="选择类型" style="width: 100%">
            <el-option
              v-for="(label, key) in USER_TYPE_LABELS"
              :key="key"
              :label="label"
              :value="Number(key)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 修改子账号 -->
    <el-dialog v-model="editDialogVisible" title="修改子账号" width="480">
      <div class="edit-user-info">
        <span>账号：{{ editForm.identifier }}</span>
        <el-tag :type="USER_TYPE_TAG[editForm.oldType]" effect="plain">{{ USER_TYPE_LABELS[editForm.oldType] }}</el-tag>
      </div>
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="editForm.password" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="新类型" prop="type">
          <el-select v-model="editForm.type" placeholder="不修改" clearable style="width: 100%">
            <el-option label="普通管理员" :value="USER_TYPE.ADMIN" />
            <el-option label="普通用户" :value="USER_TYPE.USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        类型仅支持 1（管理员）↔ 2（普通用户）互转，不可修改为超级管理员。
      </el-alert>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { UserFilled, Plus } from '@element-plus/icons-vue'
import {
  querySubaccounts,
  createSubaccount,
  updateSubaccount,
  deleteSubaccount
} from '@/api/user'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const query = reactive({ identifier: '', type: null })

const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const saving = ref(false)
const createFormRef = ref()
const editFormRef = ref()
const createForm = reactive({ identifier: '', password: '', type: null })
const editForm = reactive({ id: null, identifier: '', oldType: null, password: '', type: null })

const passwordValidator = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{8,20}$/.test(value)) {
    callback(new Error('密码需8-20位，含大小写字母和数字'))
  } else {
    callback()
  }
}

const editPasswordValidator = (rule, value, callback) => {
  if (!value) {
    callback()
  } else if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{8,20}$/.test(value)) {
    callback(new Error('密码需8-20位，含大小写字母和数字'))
  } else {
    callback()
  }
}

const createRules = {
  identifier: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9]{6,20}$/, message: '账号需为6-20位字母或数字', trigger: 'blur' }
  ],
  password: [{ required: true, validator: passwordValidator, trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const editRules = {
  password: [{ validator: editPasswordValidator, trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await querySubaccounts(
      { pageNum: pageNum.value, pageSize: pageSize.value },
      { ...query }
    )
    if (res.code === 1) {
      list.value = res.data?.rows || []
      total.value = res.data?.total || 0
    }
  } catch {
    // 忽略
  } finally {
    loading.value = false
  }
}

function searchList() {
  pageNum.value = 1
  loadList()
}

function openCreate() {
  Object.assign(createForm, { identifier: '', password: '', type: null })
  createDialogVisible.value = true
}

async function doCreate() {
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const res = await createSubaccount({ ...createForm })
      if (res.code === 1) {
        ElMessage.success('创建成功')
        createDialogVisible.value = false
        loadList()
      }
    } catch {
      // 忽略
    } finally {
      saving.value = false
    }
  })
}

function openEdit(row) {
  Object.assign(editForm, {
    id: row.subaccountId,
    identifier: row.identifier,
    oldType: row.type,
    password: '',
    type: null
  })
  editDialogVisible.value = true
}

async function doEdit() {
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!editForm.password && editForm.type == null) {
      ElMessage.warning('请至少填写一项修改内容')
      return
    }
    saving.value = true
    try {
      const data = {}
      if (editForm.password) data.password = editForm.password
      if (editForm.type != null) data.type = editForm.type
      const res = await updateSubaccount(editForm.id, data)
      if (res.code === 1) {
        ElMessage.success('修改成功')
        editDialogVisible.value = false
        loadList()
      }
    } catch {
      // 忽略
    } finally {
      saving.value = false
    }
  })
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm(`确定删除子账号「${row.identifier}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteSubaccount(row.subaccountId)
    ElMessage.success('已删除')
    loadList()
  } catch {
    // 忽略
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.admin-page {
  height: 100%;
  overflow-y: auto;
}
.account-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.account-avatar {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
}
.account-name {
  font-weight: 500;
}
.edit-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding: 12px 16px;
  background: var(--color-bg);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
}
</style>
