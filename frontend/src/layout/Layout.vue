<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? 68 : 232" class="sidebar">
      <div class="sidebar-logo" @click="router.push('/consult')">
        <div class="logo-badge">
          <svg viewBox="0 0 32 32" width="30" height="30">
            <rect width="32" height="32" rx="8" fill="#d4a857" />
            <path d="M9 22V10h3.2v9.2h5.4V22H9zm9.8 0V10H22v12h-3.2z" fill="#16324f" />
          </svg>
        </div>
        <div v-show="!collapsed" class="logo-text">
          <span class="logo-name">LexAgent</span>
          <span class="logo-sub">智能法律援助系统</span>
        </div>
      </div>

      <div v-show="!collapsed" class="nav-section-label">功能导航</div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div v-show="!collapsed" class="sidebar-version">LexAgent · v1.0</div>
        <el-button text @click="collapsed = !collapsed" class="collapse-btn">
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          <span v-show="!collapsed">收起导航</span>
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header" height="64px">
        <div class="header-left">
          <span class="page-name">{{ route.meta.title }}</span>
        </div>
        <el-dropdown @command="onCommand" trigger="click">
          <div class="user-info">
            <el-avatar :size="34" class="avatar">
              {{ userStore.identifier?.charAt(0).toUpperCase() }}
            </el-avatar>
            <div class="user-meta">
              <span class="user-name">{{ userStore.identifier }}</span>
              <span class="user-role">{{ roleLabel }}</span>
            </div>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="viewInfo">
                <el-icon><User /></el-icon>
                查看信息
              </el-dropdown-item>
              <el-dropdown-item command="editInfo">
                <el-icon><EditPen /></el-icon>
                修改信息
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <!-- :key 强制路由变化时重新挂载组件，杜绝旧组件残留 -->
        <router-view :key="route.path" />
      </el-main>
    </el-container>

    <!-- 查看个人信息 -->
    <el-dialog v-model="viewDialogVisible" title="个人信息" width="480">
      <div v-loading="infoLoading" class="info-view">
        <div class="info-avatar-row">
          <el-avatar :size="52" class="info-avatar">
            {{ userInfo.identifier?.charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="info-title">
            <div class="info-name">{{ userInfo.identifier || '-' }}</div>
            <el-tag :type="USER_TYPE_TAG[userInfo.type]" size="small" effect="plain">
              {{ userInfo.typeName || USER_TYPE_LABELS[userInfo.type] || '-' }}
            </el-tag>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">用户 ID</span>
            <span class="info-value">{{ userInfo.userId ?? '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">联系邮箱</span>
            <span class="info-value">{{ userInfo.email || '未填写' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">联系电话</span>
            <span class="info-value">{{ userInfo.phone || '未填写' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ formatDateTime(userInfo.createdAt) || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">更新时间</span>
            <span class="info-value">{{ formatDateTime(userInfo.updatedAt) || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">最近活跃</span>
            <span class="info-value">{{ formatDateTime(userInfo.lastActive) || '-' }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 修改个人信息 -->
    <el-dialog v-model="editDialogVisible" title="修改信息" width="480">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="账号" prop="identifier">
          <el-input v-model="editForm.identifier" placeholder="6-20位字母或数字" />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="editForm.password"
            type="password"
            show-password
            placeholder="8-16位，含字母和数字；留空则不修改"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="editForm.phone" placeholder="留空则不修改" maxlength="11" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>留空的字段提交时将自动跳过，保持不变。</el-alert>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSaveInfo">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { logout } from '@/api/auth'
import { getUserInfo, updateUserInfo } from '@/api/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

// 菜单激活项必须响应式跟随真实路由，否则 el-menu router 模式下
// 内部 active 状态会与路由 desync，导致点击"它以为已激活"的项时跳过 push
const activeMenu = computed(() => route.path)

// 不使用 el-menu 的 router 模式（其内部状态机在重复点击/程序化导航后有 desync 边界问题），
// 改为手动 router.push，确保每次点击都真实跳转
function handleMenuSelect(index) {
  if (index === route.path) return // 已在当前页，避免 NavigationDuplicated
  router.push(index)
}

const menuItems = computed(() => {
  const items = [
    { path: '/consult', title: '法律问诊', icon: 'ChatDotRound' },
    { path: '/search', title: '检索测试', icon: 'Search' }
  ]
  if (userStore.isAdmin) {
    items.splice(1, 0, { path: '/knowledge', title: '知识库管理', icon: 'FolderOpened' })
  }
  if (userStore.isSuperAdmin) {
    items.push({ path: '/admin', title: '子账号管理', icon: 'UserFilled' })
  }
  return items
})

const roleLabel = computed(() => USER_TYPE_LABELS[userStore.userType] || '普通用户')

// ===== 个人信息：查看 / 修改 =====
const viewDialogVisible = ref(false)
const editDialogVisible = ref(false)
const infoLoading = ref(false)
const saving = ref(false)
const editFormRef = ref()
const userInfo = ref({})
const editForm = reactive({ identifier: '', password: '', email: '', phone: '' })

// 留空字段不校验不提交（与后端"留空不修改"契约一致）。
// 注意：UserInfoUpdateDTO.identifier 后端无格式校验（存量账号如 dawn 仅4位），前端也不加 pattern
const editRules = {
  password: [{ pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,16}$/, message: '密码需8-16位，包含字母和数字', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

async function openViewInfo() {
  viewDialogVisible.value = true
  infoLoading.value = true
  try {
    const res = await getUserInfo()
    if (res.code === 1) userInfo.value = res.data || {}
  } catch {
    // 错误已在拦截器提示
  } finally {
    infoLoading.value = false
  }
}

async function openEditInfo() {
  editDialogVisible.value = true
  try {
    const res = await getUserInfo()
    if (res.code === 1 && res.data) {
      Object.assign(editForm, {
        identifier: res.data.identifier || '',
        password: '',
        email: res.data.email || '',
        phone: res.data.phone || ''
      })
    }
  } catch {
    // 错误已在拦截器提示
  }
}

async function doSaveInfo() {
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    // 只提交非空字段（后端契约：留空不修改）
    const payload = {}
    if (editForm.identifier) payload.identifier = editForm.identifier
    if (editForm.password) payload.password = editForm.password
    if (editForm.email) payload.email = editForm.email
    if (editForm.phone) payload.phone = editForm.phone
    if (!Object.keys(payload).length) {
      ElMessage.warning('请至少填写一项修改内容')
      return
    }
    saving.value = true
    try {
      const res = await updateUserInfo(payload)
      if (res.code === 1) {
        ElMessage.success('修改成功')
        editDialogVisible.value = false
        // 账号变更时同步顶栏与会话存储
        const newIdentifier = res.data?.identifier
        if (newIdentifier && newIdentifier !== userStore.identifier) {
          userStore.identifier = newIdentifier
          localStorage.setItem('lexagent_identifier', newIdentifier)
        }
      }
    } catch {
      // 错误已在拦截器提示
    } finally {
      saving.value = false
    }
  })
}

async function onCommand(cmd) {
  if (cmd === 'viewInfo') return openViewInfo()
  if (cmd === 'editInfo') return openEditInfo()
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    try {
      await logout()
    } catch {
      // 忽略退出请求失败
    }
    userStore.clearLoginInfo()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
/* ============ 侧边栏 ============ */
.sidebar {
  background: linear-gradient(180deg, #1d3f63 0%, #16324f 45%, #12293f 100%);
  transition: width 0.25s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 2px 0 12px rgba(18, 41, 63, 0.15);
  z-index: 10;
}
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 22px;
  cursor: pointer;
}
.logo-badge {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.25));
}
.logo-text {
  display: flex;
  flex-direction: column;
  white-space: nowrap;
}
.logo-name {
  color: #fff;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.2;
}
.logo-sub {
  color: rgba(255, 255, 255, 0.45);
  font-size: 11px;
  margin-top: 3px;
  letter-spacing: 0.14em;
}
.nav-section-label {
  padding: 4px 24px 10px;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.32);
}
.sidebar-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 0 12px;
}
.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}
:deep(.el-menu) {
  background: transparent;
}
:deep(.el-menu-item) {
  position: relative;
  height: 46px;
  line-height: 46px;
  margin: 4px 0;
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 14px;
  transition: background 0.18s, color 0.18s;
}
:deep(.el-menu-item .el-icon) {
  font-size: 18px;
}
:deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(212, 168, 87, 0.22) 0%, rgba(212, 168, 87, 0.08) 100%);
  color: var(--color-accent-light);
  font-weight: 600;
}
:deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 22px;
  border-radius: 0 3px 3px 0;
  background: var(--color-accent);
  box-shadow: 0 0 10px rgba(212, 168, 87, 0.5);
}
/* 收起状态下的菜单项居中 */
:deep(.el-menu--collapse .el-menu-item) {
  padding: 0;
  justify-content: center;
}
.sidebar-footer {
  padding: 14px 16px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}
.sidebar-version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.28);
  text-align: center;
  margin-bottom: 10px;
  letter-spacing: 0.05em;
}
.collapse-btn {
  color: rgba(255, 255, 255, 0.45);
  width: 100%;
  font-size: 13px;
}
.collapse-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

/* ============ 顶栏 ============ */
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border-light);
  box-shadow: 0 1px 4px rgba(16, 24, 40, 0.03);
  padding: 0 28px;
  z-index: 5;
}
.header-left {
  display: flex;
  align-items: center;
}
.page-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-primary);
  position: relative;
  padding-left: 14px;
}
.page-name::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--color-accent) 0%, var(--color-accent-dark) 100%);
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 10px;
  transition: background 0.2s;
}
.user-info:hover {
  background: var(--color-bg);
}
.avatar {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  color: var(--color-accent-light);
  font-weight: 600;
  font-size: 15px;
}
.user-meta {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--color-text);
}
.user-role {
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 1px;
}
.user-caret {
  color: var(--color-text-muted);
  font-size: 12px;
}

/* ============ 主区域 ============ */
.main {
  background: var(--color-bg);
  padding: 0;
  overflow: hidden;
}

/* ============ 个人信息弹窗 ============ */
.info-avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 4px 18px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 18px;
}
.info-avatar {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  color: var(--color-accent-light);
  font-weight: 600;
  font-size: 20px;
  flex-shrink: 0;
}
.info-title {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.info-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-primary);
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
}
.info-item {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 9px 4px;
  border-bottom: 1px dashed var(--color-border-light);
}
.info-item:nth-last-child(-n + 2) {
  border-bottom: none;
}
.info-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  min-width: 56px;
}
.info-value {
  font-size: 13px;
  color: var(--color-text);
  word-break: break-all;
}
</style>
