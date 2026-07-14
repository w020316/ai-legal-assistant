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
    <!-- 左半：公报封面式品牌叙事（深墨面板） -->
    <aside class="brand-panel">
      <!-- 公报案头 -->
      <div class="cover-masthead">
        <span class="cover-eyebrow">EST. MMXXVI · VOL. I</span>
        <div class="cover-divider"></div>
      </div>
      <div class="brand-inner">
        <div class="brand-mark">
          <el-icon :size="24" color="#9A6B2F"><Reading /></el-icon>
          <span class="brand-name">linzAI</span>
        </div>
        <h1 class="brand-title">
          <span class="title-line">法律智能</span>
          <span class="title-line">问答平台</span>
        </h1>
        <p class="brand-sub">
          面向法律从业者的检索增强问答与文书分析工具，基于权威法律语料训练，提供可追溯的引用来源。
        </p>
        <!-- 公报式装饰分隔 -->
        <div class="ornament-rule">
          <span class="ornament">❦</span>
        </div>
        <ul class="brand-features">
          <li>
            <span class="feature-numeral">I</span>
            <el-icon :size="15" color="#9A6B2F"><ChatDotRound /></el-icon>
            <span>智能问答，流式输出，引用可溯</span>
          </li>
          <li>
            <span class="feature-numeral">II</span>
            <el-icon :size="15" color="#9A6B2F"><Document /></el-icon>
            <span>文书分析，风险识别，条款建议</span>
          </li>
          <li>
            <span class="feature-numeral">III</span>
            <el-icon :size="15" color="#9A6B2F"><Lock /></el-icon>
            <span>数据隔离，权限可控，本地部署</span>
          </li>
        </ul>
        <footer class="brand-foot">
          <em>本平台回答由 AI 生成，仅供参考，不构成法律意见。</em>
        </footer>
      </div>
    </aside>

    <!-- 右半：表单区（象牙纸） -->
    <main class="form-panel">
      <div class="form-card">
        <header class="form-head">
          <span class="form-eyebrow">{{ isLogin ? 'SIGN IN' : 'REGISTER' }}</span>
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

// ===== 左半：公报封面（深墨面板） =====
.brand-panel {
  position: relative;
  overflow: hidden;
  background: var(--color-bg-deep);
  padding: 56px 56px 48px;
  display: flex;
  flex-direction: column;
  // 纸张纹理：极浅的竖线（模拟纸张纤维）
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background-image: repeating-linear-gradient(
      90deg,
      rgba(154, 107, 47, 0.015) 0px,
      rgba(154, 107, 47, 0.015) 1px,
      transparent 1px,
      transparent 3px
    );
    pointer-events: none;
  }
  // 右上角牛血红光晕（法章意象）
  &::before {
    content: '';
    position: absolute;
    top: -100px;
    right: -100px;
    width: 320px;
    height: 320px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(122, 31, 43, 0.22) 0%, transparent 65%);
    pointer-events: none;
  }
}
// 公报案头
.cover-masthead {
  position: relative;
  z-index: 1;
  margin-bottom: 48px;
}
.cover-eyebrow {
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.3em;
  color: var(--color-gilt);
  text-transform: uppercase;
}
.cover-divider {
  margin-top: 8px;
  height: 1px;
  background: linear-gradient(90deg, var(--color-gilt), transparent);
}
.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 460px;
  width: 100%;
  margin-top: auto;
  margin-bottom: auto;
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
  animation: fadeInUp 0.6s var(--ease-out) both;
}
.brand-name {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 600;
  color: #FBF8F1;
  letter-spacing: -0.01em;
}
.brand-title {
  font-family: var(--font-display);
  font-size: 56px;
  font-weight: 600;
  line-height: 1.05;
  color: #FBF8F1;
  margin-bottom: 24px;
  letter-spacing: -0.025em;
  animation: fadeInUp 0.7s var(--ease-out) both;
  animation-delay: 0.1s;
  .title-line {
    display: block;
  }
}
.brand-sub {
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.8;
  color: rgba(251, 248, 241, 0.65);
  max-width: 42ch;
  margin-bottom: 32px;
  animation: fadeInUp 0.6s var(--ease-out) both;
  animation-delay: 0.2s;
}
// 公报式装饰分隔（花纹）
.ornament-rule {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
  animation: fadeInUp 0.6s var(--ease-out) both;
  animation-delay: 0.25s;
  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: rgba(154, 107, 47, 0.3);
    max-width: 60px;
  }
  .ornament {
    font-family: var(--font-display);
    font-size: 20px;
    color: var(--color-gilt);
    opacity: 0.7;
  }
}
.brand-features {
  list-style: none;
  padding: 0;
  margin: 0 0 40px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  li {
    display: flex;
    align-items: center;
    gap: 12px;
    font-family: var(--font-serif);
    font-size: 14px;
    color: rgba(251, 248, 241, 0.75);
    animation: fadeInUp 0.5s var(--ease-out) both;
    &:nth-child(1) { animation-delay: 0.3s; }
    &:nth-child(2) { animation-delay: 0.4s; }
    &:nth-child(3) { animation-delay: 0.5s; }
  }
}
.feature-numeral {
  font-family: var(--font-display);
  font-style: italic;
  font-size: 14px;
  color: var(--color-gilt);
  opacity: 0.7;
  width: 24px;
  text-align: center;
}
.brand-foot {
  font-family: var(--font-serif);
  font-size: 12px;
  font-style: italic;
  color: rgba(251, 248, 241, 0.35);
  padding-top: 24px;
  border-top: 1px solid rgba(154, 107, 47, 0.15);
  max-width: 50ch;
}

// ===== 右半：表单区（象牙纸） =====
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 48px;
  background: var(--color-bg);
  position: relative;
}
.form-card {
  width: 100%;
  max-width: 380px;
}
.form-head {
  margin-bottom: 32px;
  .form-eyebrow {
    display: block;
    font-family: var(--font-sans);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.25em;
    color: var(--color-accent);
    text-transform: uppercase;
    margin-bottom: 8px;
  }
  h2 {
    font-family: var(--font-display);
    font-size: 32px;
    font-weight: 600;
    color: var(--color-primary);
    margin-bottom: 8px;
    letter-spacing: -0.02em;
  }
  p {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 14px;
    color: var(--color-text-secondary);
  }
}
.submit-btn {
  width: 100%;
  height: 46px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.toggle {
  text-align: center;
  margin-top: 24px;
  font-family: var(--font-serif);
  font-size: 14px;
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
    border-bottom: none;
    transition: var(--transition-fast);
    &:hover {
      color: var(--color-accent);
    }
  }
  span {
    color: var(--color-text-secondary);
  }
}

// 响应式：移动端隐藏品牌叙事
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
