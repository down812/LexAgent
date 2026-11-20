<template>
  <div class="page-container knowledge-page">
    <div class="page-header">
      <div class="page-header-icon"><el-icon><FolderOpened /></el-icon></div>
      <div class="page-header-text">
        <h2>知识库管理</h2>
        <p>管理法律知识数据集与文档，为检索增强生成（RAG）提供知识支撑</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="knowledge-tabs">
      <!-- 数据集 -->
      <el-tab-pane label="数据集" name="datasets">
        <div class="card">
          <div class="toolbar">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="名称">
                <el-input v-model="dsQuery.name" placeholder="知识库名称" clearable style="width: 200px" @keyup.enter="searchDatasets" />
              </el-form-item>
              <el-form-item label="领域">
                <el-select v-model="dsQuery.domain" placeholder="全部" clearable style="width: 150px">
                  <el-option v-for="d in LEGAL_DOMAINS" :key="d.value" :label="d.label" :value="d.value" />
                </el-select>
              </el-form-item>
              <el-button type="primary" @click="searchDatasets">查询</el-button>
              <el-button type="success" @click="openDatasetDialog(null)">
                <el-icon><Plus /></el-icon> 新建知识库
              </el-button>
            </el-form>
          </div>

          <el-table :data="datasets" v-loading="dsLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="140">
              <template #default="{ row }">
                <span class="ds-name">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="domain" label="领域" width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.domain }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileCount" label="文件数" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain" round>{{ row.fileCount }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="viewFiles(row)">文件</el-button>
                <el-button text type="warning" @click="openDatasetDialog(row)">编辑</el-button>
                <el-button text type="danger" @click="removeDataset(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="dsPageNum"
              v-model:page-size="dsPageSize"
              :total="dsTotal"
              layout="total, prev, pager, next"
              @current-change="loadDatasets"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 文件管理 -->
      <el-tab-pane label="文件管理" name="files">
        <div class="card">
          <div class="toolbar">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="知识库">
                <el-select v-model="fileQuery.datasetId" placeholder="全部" clearable style="width: 190px" @change="searchFiles">
                  <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="fileQuery.status" placeholder="全部" clearable style="width: 140px" @change="searchFiles">
                  <el-option
                    v-for="(label, key) in FILE_STATUS_LABELS"
                    :key="key"
                    :label="label"
                    :value="key"
                  />
                </el-select>
              </el-form-item>
              <el-button type="primary" @click="searchFiles">查询</el-button>
              <el-upload
                :show-file-list="false"
                :before-upload="beforeUpload"
                accept=".pdf,.docx,.txt,.md,.jpg,.jpeg,.png"
                style="display: inline-block; margin-left: 8px"
              >
                <el-button type="success">
                  <el-icon><Upload /></el-icon> 上传文件
                </el-button>
              </el-upload>
            </el-form>
          </div>

          <el-table :data="files" v-loading="fileLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="file-name"><el-icon><Document /></el-icon> {{ row.fileName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileType" label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain">{{ row.fileType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="FILE_STATUS_TAG[row.status]" size="small">
                  {{ FILE_STATUS_LABELS[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="Chunk数" width="90" align="center" />
            <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip />
            <el-table-column prop="uploadTime" label="上传时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.uploadTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="refreshFile(row)">刷新</el-button>
                <el-button text type="danger" @click="removeFile(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="filePageNum"
              v-model:page-size="filePageSize"
              :total="fileTotal"
              layout="total, prev, pager, next"
              @current-change="loadFiles"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- OCR 服务 -->
      <el-tab-pane label="OCR 服务" name="ocr">
        <div class="card">
          <div class="ocr-status-card" :class="{ online: ocrStatus.available }">
            <div class="ocr-status-icon" :class="{ online: ocrStatus.available }">
              <el-icon :size="34">
                <component :is="ocrStatus.available ? 'CircleCheck' : 'CircleClose'" />
              </el-icon>
            </div>
            <div class="ocr-status-info">
              <h3>{{ ocrStatus.available ? 'OCR 服务在线' : 'OCR 服务离线' }}</h3>
              <p>{{ ocrStatus.message || '独立 Python OCR 微服务（PaddleOCR），为图片与扫描件提供文字识别能力' }}</p>
              <div class="ocr-tags">
                <el-tag :type="ocrStatus.enabled ? 'success' : 'info'" effect="plain">enabled: {{ ocrStatus.enabled }}</el-tag>
                <el-tag :type="ocrStatus.available ? 'success' : 'danger'" effect="plain">available: {{ ocrStatus.available }}</el-tag>
              </div>
            </div>
            <el-button @click="loadOcrStatus">刷新状态</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 数据集表单对话框 -->
    <el-dialog v-model="dsDialogVisible" :title="editingDs.id ? '编辑知识库' : '新建知识库'" width="500">
      <el-form :model="editingDs" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editingDs.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="领域" required>
          <el-select v-model="editingDs.domain" placeholder="选择法律领域" style="width: 100%">
            <el-option v-for="d in LEGAL_DOMAINS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editingDs.description" type="textarea" :rows="3" placeholder="简要描述该知识库的用途与内容范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDataset">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="420">
      <el-form label-width="80px">
        <el-form-item label="知识库" required>
          <el-select v-model="uploadTarget.datasetId" placeholder="选择目标知识库" style="width: 100%">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="uploadTarget.file" label="文件">
          <span>{{ uploadTarget.file.name }} ({{ formatSize(uploadTarget.file.size) }})</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { FolderOpened, Plus, Upload, Document } from '@element-plus/icons-vue'
import {
  queryDatasets,
  createDataset,
  updateDataset,
  deleteDataset,
  queryFiles,
  deleteFile,
  getFile,
  uploadFile,
  getOcrStatus
} from '@/api/knowledge'

const activeTab = ref('datasets')

// ===== 数据集 =====
const dsLoading = ref(false)
const datasets = ref([])
const dsTotal = ref(0)
const dsPageNum = ref(1)
const dsPageSize = ref(20)
const dsQuery = reactive({ name: '', domain: '' })
const dsDialogVisible = ref(false)
const editingDs = reactive({ id: null, name: '', domain: '', description: '' })

async function loadDatasets() {
  dsLoading.value = true
  try {
    const res = await queryDatasets(
      { pageNum: dsPageNum.value, pageSize: dsPageSize.value },
      { ...dsQuery }
    )
    if (res.code === 1) {
      datasets.value = res.data?.rows || []
      dsTotal.value = res.data?.total || 0
    }
  } catch {
    // 忽略
  } finally {
    dsLoading.value = false
  }
}

function searchDatasets() {
  dsPageNum.value = 1
  loadDatasets()
}

function openDatasetDialog(row) {
  if (row) {
    Object.assign(editingDs, {
      id: row.id,
      name: row.name,
      domain: row.domain,
      description: row.description || ''
    })
  } else {
    Object.assign(editingDs, { id: null, name: '', domain: '', description: '' })
  }
  dsDialogVisible.value = true
}

async function saveDataset() {
  if (!editingDs.name || !editingDs.domain) {
    ElMessage.warning('请填写名称和领域')
    return
  }
  try {
    if (editingDs.id) {
      await updateDataset(editingDs)
      ElMessage.success('修改成功')
    } else {
      await createDataset(editingDs)
      ElMessage.success('创建成功')
    }
    dsDialogVisible.value = false
    loadDatasets()
  } catch {
    // 忽略
  }
}

async function removeDataset(row) {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${row.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDataset(row.id)
    ElMessage.success('已删除')
    loadDatasets()
  } catch {
    // 忽略
  }
}

function viewFiles(row) {
  activeTab.value = 'files'
  fileQuery.datasetId = row.id
  searchFiles()
}

// ===== 文件 =====
const fileLoading = ref(false)
const files = ref([])
const fileTotal = ref(0)
const filePageNum = ref(1)
const filePageSize = ref(20)
const fileQuery = reactive({ datasetId: null, status: '' })
const uploadDialogVisible = ref(false)
const uploadTarget = reactive({ datasetId: null, file: null })
const uploading = ref(false)

async function loadFiles() {
  fileLoading.value = true
  try {
    const res = await queryFiles(
      { pageNum: filePageNum.value, pageSize: filePageSize.value },
      { ...fileQuery }
    )
    if (res.code === 1) {
      files.value = res.data?.rows || []
      fileTotal.value = res.data?.total || 0
    }
  } catch {
    // 忽略
  } finally {
    fileLoading.value = false
  }
}

function searchFiles() {
  filePageNum.value = 1
  loadFiles()
}

function beforeUpload(file) {
  uploadTarget.file = file
  if (!uploadTarget.datasetId) {
    uploadDialogVisible.value = true
  } else {
    doUpload()
  }
  return false
}

async function doUpload() {
  if (!uploadTarget.datasetId) {
    ElMessage.warning('请选择目标知识库')
    return
  }
  uploading.value = true
  try {
    const res = await uploadFile(uploadTarget.datasetId, uploadTarget.file)
    if (res.code === 1) {
      ElMessage.success('上传成功，正在后台解析')
      uploadDialogVisible.value = false
      uploadTarget.file = null
      loadFiles()
    }
  } catch {
    // 忽略
  } finally {
    uploading.value = false
  }
}

async function refreshFile(row) {
  try {
    const res = await getFile(row.id)
    if (res.code === 1) {
      const idx = files.value.findIndex((f) => f.id === row.id)
      if (idx >= 0) Object.assign(files.value[idx], res.data)
      ElMessage.success('状态已刷新')
    }
  } catch {
    // 忽略
  }
}

async function removeFile(row) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.fileName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteFile(row.id)
    ElMessage.success('已删除')
    loadFiles()
    loadDatasets()
  } catch {
    // 忽略
  }
}

// ===== OCR 状态 =====
const ocrStatus = reactive({ enabled: false, available: false, message: '' })

async function loadOcrStatus() {
  try {
    const res = await getOcrStatus()
    if (res.code === 1) {
      Object.assign(ocrStatus, res.data)
    }
  } catch {
    // 忽略
  }
}

onMounted(() => {
  loadDatasets()
  loadFiles()
  loadOcrStatus()
})
</script>

<style scoped>
.knowledge-page {
  height: 100%;
  overflow-y: auto;
}
.knowledge-tabs {
  background: transparent;
}
.knowledge-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  padding: 0 24px;
  height: 42px;
}
.ds-name {
  font-weight: 600;
  color: var(--color-primary);
}
.file-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.file-name .el-icon {
  color: var(--color-text-muted);
  flex-shrink: 0;
}
/* OCR 状态卡片 */
.ocr-status-card {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 24px;
  border-radius: 12px;
  background: #fafbfc;
  border: 1px solid var(--color-border-light);
}
.ocr-status-card.online {
  background: linear-gradient(90deg, rgba(103, 194, 58, 0.05) 0%, #fafbfc 60%);
  border-color: rgba(103, 194, 58, 0.2);
}
.ocr-status-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: #eef2f7;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ocr-status-icon.online {
  background: rgba(103, 194, 58, 0.12);
  color: var(--color-success);
}
.ocr-status-info {
  flex: 1;
}
.ocr-status-info h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 6px;
}
.ocr-status-info p {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
}
.ocr-tags {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
</style>
