<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ChatDotRound, Monitor, User, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const menus = [
  { index: '/chat', title: '智能问答', icon: ChatDotRound },
  { index: '/health', title: '系统状态', icon: Monitor },
]

function handleMenuSelect(index: string) {
  router.push(index)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <!-- 顶部栏 -->
    <el-header class="header">
      <div class="header-left">
        <span class="logo">AI 法律助手</span>
      </div>
      <div class="header-right">
        <el-dropdown @command="(cmd: string) => cmd === 'logout' && handleLogout()">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span class="username">{{ userStore.username || '未登录' }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <!-- 侧边栏 -->
      <el-aside :width="isCollapse ? '64px' : '210px'" class="aside">
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.title }}</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: var(--color-primary);
  color: #fff;
  padding: 0 24px;
  height: 56px;
}
.header-left .logo {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #fff;
}
.username {
  font-size: 14px;
}
.aside {
  background-color: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  transition: width 0.2s;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.main {
  background-color: var(--color-bg);
  padding: 16px;
}
</style>
