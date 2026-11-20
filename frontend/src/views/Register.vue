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
          创建账号，<br />开启<em>智能法律之旅</em>
        </h1>
        <p class="auth-slogan-sub">
          注册即可体验多智能体协作的法律问诊服务，<br />
          让复杂的法律问题变得清晰可解。
        </p>

        <div class="auth-features">
          <div class="auth-feature">
            <div class="auth-feature-icon"><el-icon><ChatDotRound /></el-icon></div>
            <div>
              <h4>智能法律问诊</h4>
              <p>描述您的法律问题，AI 多智能体协作给出结构化专业解答</p>
            </div>
          </div>
          <div class="auth-feature">
            <div class="auth-feature-icon"><el-icon><Reading /></el-icon></div>
            <div>
              <h4>真实法律依据</h4>
              <p>每一条解答均引用真实法条，经核验审核，杜绝凭空捏造</p>
            </div>
          </div>
          <div class="auth-feature">
            <div class="auth-feature-icon"><el-icon><Picture /></el-icon></div>
            <div>
              <h4>文档 OCR 识别</h4>
              <p>支持上传图片与 PDF，自动识别合同、文书内容并提问</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧注册表单 -->
    <div class="auth-panel">
      <div class="auth-form">
        <h2 class="auth-form-title">创建账号</h2>
        <p class="auth-form-sub">填写以下信息，一分钟完成注册</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleRegister">
          <el-form-item label="用户名" prop="identifier">
            <el-input v-model="form.identifier" placeholder="请输入用户名" :prefix-icon="User" size="large" />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱" :prefix-icon="Message" size="large" />
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="form.code" placeholder="请输入邮箱验证码" :prefix-icon="Key" size="large" />
              <el-button size="large" :disabled="countdown > 0" :loading="codeLoading" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="8-16位，含字母和数字"
              :prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleRegister">
            注 册
          </el-button>

          <div class="auth-footer">
            已有账号？
            <router-link to="/login">返回登录</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { User, Lock, Message, Key } from '@element-plus/icons-vue'
import { register } from '@/api/auth'
import { sendEmailCode } from '@/api/user'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const codeLoading = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({ identifier: '', email: '', code: '', password: '' })

const validatePassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,16}$/.test(value)) {
    callback(new Error('密码需8-16位，包含字母和数字'))
  } else {
    callback()
  }
}

const rules = {
  identifier: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }]
}

async function sendCode() {
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  codeLoading.value = true
  try {
    const res = await sendEmailCode(form.email)
    if (res.code === 1) {
      ElMessage.success(res.msg || '验证码已发送')
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) clearInterval(timer)
      }, 1000)
    }
  } catch {
    // 错误已处理
  } finally {
    codeLoading.value = false
  }
}

async function handleRegister() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await register(form)
      if (res.code === 1 && res.data) {
        ElMessage.success('注册成功，已自动登录')
        router.push('/consult')
      }
    } catch {
      // 错误已处理
    } finally {
      loading.value = false
    }
  })
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
/* 布局样式复用全局 main.css 中的 .auth-* 设计系统 */
</style>
