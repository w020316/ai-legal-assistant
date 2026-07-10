<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, register } from '@/api'
import { useUserStore } from '@/stores/user'
import { ChatDotRound, Lock, Reading, Document } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isLogin = ref(true)
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3-32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      if (isLogin.value) {
        const res = await login({ username: form.username, password: form.password })
        userStore.setAuth({
          accessToken: res.data.accessToken,
          refreshToken: res.data.refreshToken,
          username: res.data.username || form.username,
          role: res.data.role,
        })
        ElMessage.success('登录成功')
        const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/chat'
        router.push(redirect)
      } else {
        await register({ username: form.username, password: form.password, email: form.email })
        ElMessage.success('注册成功，请登录')
        isLogin.value = true
      }
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}

function toggleMode() {
  isLogin.value = !isLogin.value
  formRef.value?.resetFields()
}
</script>

<template>
  <div class="login-page">
    <!-- 左半：品牌叙事（暖白背景 + 衬线大标题 + 价值主张） -->
    <aside class="brand-panel">
      <div class="brand-inner">
        <div class="brand-mark">
          <el-icon :size="28" color="#C8893E"><Reading /></el-icon>
          <span class="brand-name">linzAI</span>
        </div>
        <h1 class="brand-title">法律智能<br />问答平台</h1>
        <p class="brand-sub">面向法律从业者的检索增强问答与文书分析工具，基于权威法律语料训练，提供可追溯的引用来源。</p>
        <ul class="brand-features">
          <li>
            <el-icon :size="16" color="#C8893E"><ChatDotRound /></el-icon>
            <span>智能问答，流式输出，引用可溯</span>
          </li>
          <li>
            <el-icon :size="16" color="#C8893E"><Document /></el-icon>
            <span>文书分析，风险识别，条款建议</span>
          </li>
          <li>
            <el-icon :size="16" color="#C8893E"><Lock /></el-icon>
            <span>数据隔离，权限可控，本地部署</span>
          </li>
        </ul>
        <footer class="brand-foot">本平台回答由 AI 生成，仅供参考，不构成法律意见。</footer>
      </div>
    </aside>

    <!-- 右半：表单区 -->
    <main class="form-panel">
      <div class="form-card">
        <header class="form-head">
          <h2>{{ isLogin ? '登录' : '注册' }}</h2>
          <p>{{ isLogin ? '欢迎回来，请输入您的凭据' : '创建账号，开始使用' }}</p>
        </header>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @submit.prevent="handleSubmit">
          <el-form-item prop="username" label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password" label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item v-if="!isLogin" prop="email" label="邮箱（选填）">
            <el-input v-model="form.email" placeholder="选填，用于找回密码" prefix-icon="Message" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="submit-btn" @click="handleSubmit">
              {{ isLogin ? '登 录' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>
        <div class="toggle">
          <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
          <el-link type="primary" :underline="false" @click="toggleMode">
            {{ isLogin ? '去注册' : '去登录' }}
          </el-link>
        </div>
        <div class="legal-links">
          <router-link to="/privacy">隐私政策</router-link>
          <span>·</span>
          <router-link to="/terms">用户协议</router-link>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100dvh;
  background: var(--color-bg);
}

// 左半品牌叙事（深色面板）
.brand-panel {
  position: relative;
  overflow: hidden;
  background: var(--color-sidebar-bg);
  padding: 64px 56px;
  display: flex;
  align-items: center;
  border-right: none;
  // 背景装饰：右上角琥珀色渐变光晕
  &::before {
    content: '';
    position: absolute;
    top: -120px;
    right: -120px;
    width: 360px;
    height: 360px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(200, 137, 62, 0.18) 0%, transparent 70%);
    pointer-events: none;
  }
  // 背景装饰：左下角细几何线条网格
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-image:
      linear-gradient(rgba(200, 192, 180, 0.04) 1px, transparent 1px),
      linear-gradient(90deg, rgba(200, 192, 180, 0.04) 1px, transparent 1px);
    background-size: 40px 40px;
    background-position: 0 0;
    mask-image: linear-gradient(to top right, rgba(0, 0, 0, 0.5), transparent 60%);
    -webkit-mask-image: linear-gradient(to top right, rgba(0, 0, 0, 0.5), transparent 60%);
    pointer-events: none;
  }
}
.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 460px;
  width: 100%;
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 64px;
  animation: fadeInUp 0.5s var(--ease-out) both;
}
.brand-name {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
  color: #F0EBE0;
  letter-spacing: 0.02em;
}
.brand-title {
  font-family: var(--font-serif);
  font-size: 52px;
  font-weight: 700;
  line-height: 1.15;
  color: #F0EBE0;
  margin-bottom: 24px;
  letter-spacing: -0.02em;
  // 渐入动画
  animation: fadeInUp 0.6s var(--ease-out) both;
  animation-delay: 0.1s;
}
.brand-sub {
  font-size: 15px;
  line-height: 1.75;
  color: rgba(200, 192, 180, 0.7);
  max-width: 42ch;
  margin-bottom: 40px;
  animation: fadeInUp 0.5s var(--ease-out) both;
  animation-delay: 0.2s;
}
.brand-features {
  list-style: none;
  padding: 0;
  margin: 0 0 48px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  li {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 14px;
    color: rgba(200, 192, 180, 0.8);
    // 逐项 staggered 入场
    animation: fadeInUp 0.5s var(--ease-out) both;
    &:nth-child(1) { animation-delay: 0.3s; }
    &:nth-child(2) { animation-delay: 0.4s; }
    &:nth-child(3) { animation-delay: 0.5s; }
  }
}
.brand-foot {
  font-size: 12px;
  color: rgba(200, 192, 180, 0.4);
  padding-top: 24px;
  border-top: 1px solid rgba(200, 192, 180, 0.1);
  max-width: 50ch;
}

// 右半表单
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 48px;
  background: var(--color-bg-card);
}
.form-card {
  width: 100%;
  max-width: 380px;
}
.form-head {
  margin-bottom: 32px;
  h2 {
    font-family: var(--font-serif);
    font-size: 26px;
    font-weight: 700;
    color: var(--color-primary);
    margin-bottom: 8px;
  }
  p {
    font-size: 13px;
    color: var(--color-text-secondary);
  }
}
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.04em;
}
.toggle {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: var(--color-text-regular);
}
.legal-links {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  font-size: 12px;
  a {
    color: var(--color-text-secondary);
    transition: var(--transition-base);
    &:hover {
      color: var(--color-accent);
    }
  }
  span {
    color: var(--color-text-secondary);
  }
}

// 响应式：移动端塌缩为单列，隐藏品牌叙事
@media (max-width: 768px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .brand-panel {
    display: none;
  }
  .form-panel {
    padding: 32px 20px;
  }
}
</style>
