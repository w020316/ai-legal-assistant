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
          <el-icon :size="18" color="#C8893E"><Reading /></el-icon>
        </span>
        <span class="logo">linzAI 法律助手</span>
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
          <div class="version">linzAI v1.0</div>
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
          <div class="version">linzAI v1.0</div>
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
  background: var(--color-sidebar-bg);
  border-bottom: none;
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
  background: rgba(200, 137, 62, 0.15);
  // 微妙呼吸光晕（LottieFiles 风格）
  animation: logoGlow 3s ease-in-out infinite;
}
// logo 徽章光晕脉冲（仅 box-shadow 呼吸，不位移）
@keyframes logoGlow {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(200, 137, 62, 0);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(200, 137, 62, 0.12);
  }
}
.header-left .logo {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: #F0EBE0;
  letter-spacing: 0.01em;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-sidebar-text);
  padding: 6px 12px;
  border-radius: var(--radius-button);
  transition: var(--transition-base);
  &:hover {
    background: rgba(200, 192, 180, 0.08);
    color: #F0EBE0;
  }
}
.username {
  font-size: 14px;
}
.aside {
  background-color: var(--color-sidebar-bg);
  border-right: none;
  transition: width 0.2s var(--ease-out);
  display: flex;
  flex-direction: column;
}
.aside-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid rgba(200, 192, 180, 0.1);
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  a {
    color: var(--color-sidebar-text);
    transition: var(--transition-base);
    &:hover {
      color: var(--color-sidebar-active);
    }
  }
  .dot {
    color: rgba(200, 192, 180, 0.3);
  }
}
.version {
  font-family: var(--font-mono);
  font-size: 11px;
  color: rgba(200, 192, 180, 0.4);
  margin-top: 8px;
  letter-spacing: 0.02em;
}
.aside :deep(.el-menu) {
  border-right: none;
  padding: 12px 8px;
  background-color: transparent;
}
.aside :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: var(--color-sidebar-text);
  background-color: transparent;
  position: relative;
  transition: var(--transition-base);
  &:hover {
    background: rgba(200, 192, 180, 0.08);
    color: #F0EBE0;
  }
  &.is-active {
    background: rgba(200, 137, 62, 0.12);
    color: var(--color-sidebar-active);
    font-weight: 500;
    // 左侧琥珀色竖条指示器（从圆点升级为竖条，更具引导力）+ 滑入动画
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 16px;
      border-radius: 2px;
      background: var(--color-sidebar-active);
      transform-origin: center;
      animation: menuBarIn 0.3s var(--ease-out) both;
    }
  }
}
// 菜单激活竖条滑入（保留垂直居中，纵向生长）
@keyframes menuBarIn {
  from {
    transform: translateY(-50%) scaleY(0);
  }
  to {
    transform: translateY(-50%) scaleY(1);
  }
}
.main {
  background-color: var(--color-bg);
  padding: 20px;
}
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out);
}
.route-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 移动端汉堡按钮 */
.menu-toggle {
  cursor: pointer;
  color: #F0EBE0;
  padding: 4px;
  border-radius: var(--radius-sm);
  transition: var(--transition-base);
  &:hover {
    color: var(--color-sidebar-active);
    background: rgba(200, 192, 180, 0.08);
  }
}

/* 抽屉内部布局：菜单 + 底部 footer 撑满高度 */
.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-sidebar-bg);
  .aside-footer {
    margin-top: auto;
  }
}
/* 抽屉内部菜单样式（锚定 .drawer-inner，teleport 后仍生效） */
.drawer-inner :deep(.el-menu) {
  border-right: none;
  padding: 12px 8px;
  background-color: transparent;
}
.drawer-inner :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: var(--color-sidebar-text);
  background-color: transparent;
  position: relative;
  transition: var(--transition-base);
  &:hover {
    background: rgba(200, 192, 180, 0.08);
    color: #F0EBE0;
  }
  &.is-active {
    background: rgba(200, 137, 62, 0.12);
    color: var(--color-sidebar-active);
    font-weight: 500;
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 16px;
      border-radius: 2px;
      background: var(--color-sidebar-active);
      transform-origin: center;
      animation: menuBarIn 0.3s var(--ease-out) both;
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
