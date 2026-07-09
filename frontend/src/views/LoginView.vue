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
        const redirect = (route.query.redirect as string) || '/chat'
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
          <el-icon :size="28" color="#8C6A3F"><Reading /></el-icon>
          <span class="brand-name">LawAI</span>
        </div>
        <h1 class="brand-title">法律智能<br />问答平台</h1>
        <p class="brand-sub">面向法律从业者的检索增强问答与文书分析工具，基于权威法律语料训练，提供可追溯的引用来源。</p>
        <ul class="brand-features">
          <li>
            <el-icon :size="16" color="#8C6A3F"><ChatDotRound /></el-icon>
            <span>智能问答，流式输出，引用可溯</span>
          </li>
          <li>
            <el-icon :size="16" color="#8C6A3F"><Document /></el-icon>
            <span>文书分析，风险识别，条款建议</span>
          </li>
          <li>
            <el-icon :size="16" color="#8C6A3F"><Lock /></el-icon>
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

// 左半品牌叙事
.brand-panel {
  position: relative;
  background: var(--color-bg);
  padding: 64px 56px;
  display: flex;
  align-items: center;
  border-right: 1px solid var(--color-border);
}
.brand-inner {
  max-width: 460px;
  width: 100%;
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 64px;
}
.brand-name {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.02em;
}
.brand-title {
  font-family: var(--font-serif);
  font-size: 48px;
  font-weight: 600;
  line-height: 1.15;
  color: var(--color-primary);
  margin-bottom: 24px;
  letter-spacing: -0.02em;
}
.brand-sub {
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-text-regular);
  max-width: 42ch;
  margin-bottom: 40px;
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
    color: var(--color-text-regular);
  }
}
.brand-foot {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
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
    font-weight: 600;
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
