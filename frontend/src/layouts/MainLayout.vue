<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
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
  Menu,
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)

// 移动端响应式：768px 以下侧边栏改为抽屉式导航
const isMobile = ref(false)
const drawerVisible = ref(false)
let mediaQuery: MediaQueryList | null = null

function updateMobile(e: MediaQueryListEvent | MediaQueryList) {
  isMobile.value = e.matches
  // 切回桌面端时关闭抽屉
  if (!e.matches) drawerVisible.value = false
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 768px)')
  isMobile.value = mediaQuery.matches
  mediaQuery.addEventListener('change', updateMobile)
})

onUnmounted(() => {
  mediaQuery?.removeEventListener('change', updateMobile)
})

const menus = [
  { index: '/chat', title: '智能问答', icon: ChatDotRound },
  { index: '/documents', title: '文档分析', icon: Document },
  { index: '/templates', title: '文书模板', icon: Files },
  { index: '/cases', title: '案例检索', icon: Search },
  { index: '/health', title: '系统状态', icon: Monitor },
]

function handleMenuSelect(index: string) {
  router.push(index)
  // 抽屉模式下点击菜单项后自动关闭抽屉
  if (isMobile.value) drawerVisible.value = false
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
        <!-- 移动端汉堡菜单按钮 -->
        <el-icon v-if="isMobile" class="menu-toggle" :size="22" @click="drawerVisible = true">
          <Menu />
        </el-icon>
        <span class="logo-badge">
          <el-icon :size="18" color="#0B2545"><Reading /></el-icon>
        </span>
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
      <!-- 侧边栏：纯白 + 右侧发丝线，240px（移动端隐藏，改为抽屉） -->
      <el-aside v-show="!isMobile" :width="isCollapse ? '64px' : '240px'" class="aside">
        <el-menu
          :default-active="route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.title }}</template>
          </el-menu-item>
        </el-menu>
        <!-- 侧边栏底部：法律链接 + 版本 -->
        <div v-if="!isCollapse" class="aside-footer">
          <div class="footer-links">
            <router-link to="/privacy">隐私政策</router-link>
            <span class="dot">·</span>
            <router-link to="/terms">用户协议</router-link>
          </div>
          <div class="version">LawAI v1.0</div>
        </div>
      </el-aside>

      <!-- 主内容区：暖白背景 -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 移动端抽屉式导航 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      size="240px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-inner">
        <el-menu
          :default-active="route.path"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.title }}</template>
          </el-menu-item>
        </el-menu>
        <!-- 抽屉底部同样展示法律链接 + 版本 -->
        <div class="aside-footer">
          <div class="footer-links">
            <router-link to="/privacy" @click="drawerVisible = false">隐私政策</router-link>
            <span class="dot">·</span>
            <router-link to="/terms" @click="drawerVisible = false">用户协议</router-link>
          </div>
          <div class="version">LawAI v1.0</div>
        </div>
      </div>
    </el-drawer>
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
  gap: 12px;
}
.logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  background: var(--color-accent-light);
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
  display: flex;
  flex-direction: column;
}
.aside-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border-light);
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  a {
    color: var(--color-text-secondary);
    transition: var(--transition-base);
    &:hover {
      color: var(--color-accent);
    }
  }
  .dot {
    color: var(--color-text-secondary);
  }
}
.version {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 8px;
  letter-spacing: 0.02em;
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
  position: relative;
  transition: var(--transition-base);
  &:hover {
    background: var(--color-bg-soft);
    color: var(--color-primary);
  }
  &.is-active {
    background: var(--color-accent-light);
    color: var(--color-primary);
    font-weight: 500;
    // 左侧古铜色小圆点指示器（非 border-left 强调，符合 ui-skill.md 4.3）
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 4px;
      height: 4px;
      border-radius: var(--radius-full);
      background: var(--color-accent);
    }
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

/* 移动端汉堡按钮 */
.menu-toggle {
  cursor: pointer;
  color: var(--color-primary);
  padding: 4px;
  border-radius: var(--radius-sm);
  transition: var(--transition-base);
  &:hover {
    color: var(--color-accent);
    background: var(--color-bg-soft);
  }
}

/* 抽屉内部布局：菜单 + 底部 footer 撑满高度 */
.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  .aside-footer {
    margin-top: auto;
  }
}
/* 抽屉内部菜单样式（锚定 .drawer-inner，teleport 后仍生效） */
.drawer-inner :deep(.el-menu) {
  border-right: none;
  padding: 12px 8px;
}
.drawer-inner :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: var(--color-text-regular);
  position: relative;
  transition: var(--transition-base);
  &:hover {
    background: var(--color-bg-soft);
    color: var(--color-primary);
  }
  &.is-active {
    background: var(--color-accent-light);
    color: var(--color-primary);
    font-weight: 500;
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 4px;
      height: 4px;
      border-radius: var(--radius-full);
      background: var(--color-accent);
    }
  }
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .header {
    padding: 0 16px;
    height: 56px;
  }
  .header-left {
    gap: 10px;
  }
  .logo {
    font-size: 16px;
  }
  .main {
    padding: 12px;
  }
}
</style>
