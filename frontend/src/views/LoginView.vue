<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, register } from '@/api'
import { useUserStore } from '@/stores/user'

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
        // M2 实现：当前后端尚未提供登录接口，此处为骨架
        const res = await login({ username: form.username, password: form.password })
        userStore.setAuth({
          accessToken: res.data.accessToken,
          refreshToken: res.data.refreshToken,
          username: form.username,
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
    <div class="login-box">
      <div class="brand">
        <el-icon :size="40" color="#1E3A5F"><ChatDotRound /></el-icon>
        <h1>AI 法律助手</h1>
        <p>面向法律从业者的智能问答与分析平台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item v-if="!isLogin" prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" prefix-icon="Message" />
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
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #1e3a5f 0%, #2c7a7b 100%);
}
.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}
.brand {
  text-align: center;
  margin-bottom: 32px;
}
.brand h1 {
  margin: 12px 0 6px;
  font-size: 22px;
  color: var(--color-primary);
}
.brand p {
  font-size: 13px;
  color: var(--color-text-regular);
}
.submit-btn {
  width: 100%;
}
.toggle {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--color-text-regular);
}
</style>
