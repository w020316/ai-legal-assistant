<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  ChatDotRound,
  Monitor,
  Document,
  Files,
  Search,
  User,
  SwitchButton,
  Reading,
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const menus = [
  { index: '/chat', title: '智能问答', icon: ChatDotRound },
  { index: '/documents', title: '文档分析', icon: Document },
  { index: '/templates', title: '文书模板', icon: Files },
  { index: '/cases', title: '案例检索', icon: Search },
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
    <!-- 顶部栏：纯白 + 底部发丝线，64px 高 -->
    <el-header class="header">
      <div class="header-left">
        <el-icon :size="20" color="#0B2545"><Reading /></el-icon>
        <span class="logo">LawAI 法律助手</span>
      </div>
      <div class="header-right">
        <el-dropdown @command="(cmd: string) => cmd === 'logout' && handleLogout()">
          <span class="user-info">
            <el-icon :size="16"><User /></el-icon>
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
      <!-- 侧边栏：纯白 + 右侧发丝线，240px -->
      <el-aside :width="isCollapse ? '64px' : '240px'" class="aside">
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

      <!-- 主内容区：暖白背景 -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" :key="$route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  min-height: 100dvh;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  padding: 0 32px;
  height: 64px;
  box-shadow: none;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-left .logo {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.01em;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-text-regular);
  padding: 6px 12px;
  border-radius: var(--radius-button);
  transition: var(--transition-base);
  &:hover {
    background: var(--color-bg-soft);
    color: var(--color-primary);
  }
}
.username {
  font-size: 14px;
}
.aside {
  background-color: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  transition: width 0.2s var(--ease-out);
}
.aside :deep(.el-menu) {
  border-right: none;
  padding: 12px 8px;
}
.aside :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: var(--color-text-regular);
  transition: var(--transition-base);
  &:hover {
    background: var(--color-bg-soft);
    color: var(--color-primary);
  }
  &.is-active {
    background: var(--color-accent-light);
    color: var(--color-primary);
    font-weight: 500;
  }
}
.main {
  background-color: var(--color-bg);
  padding: 20px;
}
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.15s ease;
}
.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
}
</style>
