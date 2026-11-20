<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="auth-brand">
      <div class="auth-brand-inner">
        <div class="auth-logo">
          <svg viewBox="0 0 32 32" width="42" height="42">
            <rect width="32" height="32" rx="8" fill="#d4a857" />
            <path d="M9 22V10h3.2v9.2h5.4V22H9zm9.8 0V10H22v12h-3.2z" fill="#16324f" />
          </svg>
          <span class="auth-logo-name">LexAgent</span>
          <span class="auth-logo-tag">LEGAL AI</span>
        </div>

        <h1 class="auth-slogan">
          让专业法律援助<br />触手可及，<em>有问必答</em>
        </h1>
        <p class="auth-slogan-sub">
          基于多智能体协作与检索增强生成的智能法律援助系统，<br />
          为每一个法律问题提供有据可依的专业解答。
        </p>

        <div class="auth-features">
          <div class="auth-feature" v-for="f in features" :key="f.title">
            <div class="auth-feature-icon">
              <el-icon><component :is="f.icon" /></el-icon>
            </div>
            <div>
              <h4>{{ f.title }}</h4>
              <p>{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="auth-panel">
      <div class="auth-form">
        <h2 class="auth-form-title">欢迎回来</h2>
        <p class="auth-form-sub">登录您的账号，继续智能法律咨询</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="登录账号" prop="identifier">
            <el-input v-model="form.identifier" placeholder="请输入用户名或邮箱" :prefix-icon="User" size="large" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>

          <div class="auth-footer">
            还没有账号？
            <router-link to="/register">立即注册</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ identifier: '', password: '' })
const rules = {
  identifier: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const features = [
  { title: '多 Agent 协作', desc: '案件分析 → 法律检索 → 法律分析 → 核验审核', icon: 'Connection' },
  { title: 'RAG 知识库', desc: '向量 + 关键词混合检索，多特征重排防幻觉', icon: 'FolderOpened' },
  { title: 'SSE 流式输出', desc: '实时展示各 Agent 处理过程与法律依据', icon: 'Promotion' }
]

async function handleLogin() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(form)
      if (res.code === 1 && res.data) {
        userStore.setLoginInfo(res.data)
        ElMessage.success('登录成功')
        router.push('/consult')
      }
    } catch {
      // 错误已在拦截器处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
/* 布局样式复用全局 main.css 中的 .auth-* 设计系统 */
</style>
