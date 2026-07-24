<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import VersionUpdateDialog from '@/components/common/VersionUpdateDialog.vue'
import {
  ChatDotRound,
  Monitor,
  Document,
  Files,
  Search,
  User,
  SwitchButton,
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

// 公报式菜单：带罗马数字章节编号
const menus = [
  { index: '/chat', title: '智能问答', icon: ChatDotRound, numeral: 'I' },
  { index: '/documents', title: '文档分析', icon: Document, numeral: 'II' },
  { index: '/templates', title: '文书模板', icon: Files, numeral: 'III' },
  { index: '/cases', title: '案例检索', icon: Search, numeral: 'IV' },
  { index: '/health', title: '系统状态', icon: Monitor, numeral: 'V' },
]

function handleMenuSelect(index: string) {
  router.push(index)
  if (isMobile.value) drawerVisible.value = false
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <!-- 顶栏：公报案头式 -->
    <el-header class="header">
      <div class="header-left">
        <el-icon v-if="isMobile" class="menu-toggle" :size="22" @click="drawerVisible = true">
          <Menu />
        </el-icon>
        <div class="masthead">
          <span class="masthead-eyebrow">EST. MMXXVI</span>
          <span class="logo">linzAI</span>
          <span class="masthead-tag">法律助手</span>
        </div>
      </div>
      <div class="header-right">
        <el-dropdown @command="(cmd: string) => cmd === 'logout' && handleLogout()">
          <span class="user-info">
            <el-icon :size="15"><User /></el-icon>
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
      <!-- 侧边栏：公报式深墨面板 + 罗马数字章节编号 -->
      <el-aside v-show="!isMobile" :width="isCollapse ? '64px' : '240px'" class="aside">
        <el-menu
          :default-active="route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>
              <span class="menu-numeral">{{ m.numeral }}</span>
              <span class="menu-title">{{ m.title }}</span>
            </template>
          </el-menu-item>
        </el-menu>
        <!-- 侧边栏底部：法律链接 + 版本 -->
        <div v-if="!isCollapse" class="aside-footer">
          <div class="footer-eyebrow">法务声明</div>
          <div class="footer-links">
            <router-link to="/privacy">隐私政策</router-link>
            <span class="dot">·</span>
            <router-link to="/terms">用户协议</router-link>
          </div>
          <div class="version">linzAI v1.4.3 · The Verdict</div>
        </div>
      </el-aside>

      <!-- 主内容区：象牙纸背景 -->
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
      size="280px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-inner">
        <div class="drawer-masthead">
          <span class="masthead-eyebrow">EST. MMXXVI</span>
          <span class="logo">linzAI</span>
        </div>
        <el-menu
          :default-active="route.path"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>
              <span class="menu-numeral">{{ m.numeral }}</span>
              <span class="menu-title">{{ m.title }}</span>
            </template>
          </el-menu-item>
        </el-menu>
        <div class="aside-footer">
          <div class="footer-eyebrow">法务声明</div>
          <div class="footer-links">
            <router-link to="/privacy" @click="drawerVisible = false">隐私政策</router-link>
            <span class="dot">·</span>
            <router-link to="/terms" @click="drawerVisible = false">用户协议</router-link>
          </div>
          <div class="version">linzAI v1.4.3 · The Verdict</div>
        </div>
      </div>
    </el-drawer>

    <!-- 版本更新公告弹窗：登录后展示，localStorage 记录已读 -->
    <VersionUpdateDialog />
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  min-height: 100dvh;
}
// ===== 顶栏：公报案头 =====
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-primary);
  border-bottom: 3px double var(--color-accent);
  padding: 0 var(--space-xl);
  height: 64px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
// 公报案头：EST. 年份 + 品牌 + 副标题
.masthead {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.masthead-eyebrow {
  font-family: var(--font-sans);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.25em;
  color: var(--color-gilt);
  text-transform: uppercase;
}
.logo {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: #FBF8F1;
  letter-spacing: -0.01em;
}
.masthead-tag {
  font-family: var(--font-serif);
  font-size: 13px;
  font-style: italic;
  color: rgba(251, 248, 241, 0.6);
  letter-spacing: 0.02em;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: rgba(251, 248, 241, 0.8);
  padding: 6px 12px;
  border: 1px solid transparent;
  transition: var(--transition-fast);
  &:hover {
    color: #FBF8F1;
    border-color: var(--color-accent);
    background: rgba(122, 31, 43, 0.25);
  }
}
.username {
  font-family: var(--font-sans);
  font-size: 13px;
}

// ===== 侧边栏：深墨面板 =====
.aside {
  background-color: var(--color-primary);
  border-right: 1px solid var(--color-primary-light);
  transition: width 0.25s var(--ease-out);
  display: flex;
  flex-direction: column;
}
.aside-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid rgba(251, 248, 241, 0.08);
}
.footer-eyebrow {
  font-family: var(--font-sans);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.25em;
  color: var(--color-gilt);
  text-transform: uppercase;
  margin-bottom: 8px;
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  a {
    color: rgba(251, 248, 241, 0.6);
    border-bottom: none;
    transition: var(--transition-fast);
    &:hover {
      color: var(--color-accent-soft);
    }
  }
  .dot {
    color: rgba(251, 248, 241, 0.3);
  }
}
.version {
  font-family: var(--font-mono);
  font-size: 10px;
  color: rgba(251, 248, 241, 0.35);
  margin-top: 8px;
  letter-spacing: 0.02em;
}
.aside :deep(.el-menu) {
  border-right: none;
  padding: 16px 12px;
  background-color: transparent;
}
.aside :deep(.el-menu-item) {
  height: 46px;
  line-height: 46px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: rgba(251, 248, 241, 0.7);
  background-color: transparent;
  position: relative;
  transition: var(--transition-fast);
  font-family: var(--font-serif);
  &:hover {
    background: rgba(251, 248, 241, 0.05);
    color: #FBF8F1;
  }
  &.is-active {
    background: rgba(122, 31, 43, 0.18);
    color: var(--color-accent-soft);
    font-weight: 500;
    // 左侧牛血红竖条指示器
    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 60%;
      background: var(--color-accent);
      animation: menuBarIn 0.3s var(--ease-out) both;
    }
  }
}
// 菜单项内部：罗马数字 + 标题
.menu-numeral {
  font-family: var(--font-display);
  font-style: italic;
  font-size: 13px;
  color: var(--color-gilt);
  margin-right: 10px;
  opacity: 0.7;
}
.menu-title {
  font-size: 14px;
}
.is-active .menu-numeral {
  color: var(--color-accent-soft);
  opacity: 1;
}
@keyframes menuBarIn {
  from { transform: translateY(-50%) scaleY(0); }
  to { transform: translateY(-50%) scaleY(1); }
}
.main {
  background-color: var(--color-bg);
  padding: 20px;
}
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.25s var(--ease-out), transform 0.25s var(--ease-out);
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
  color: #FBF8F1;
  padding: 4px;
  transition: var(--transition-fast);
  &:hover {
    color: var(--color-accent-soft);
  }
}

/* 抽屉内部 */
.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-primary);
}
.drawer-masthead {
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(251, 248, 241, 0.08);
  display: flex;
  align-items: baseline;
  gap: 8px;
  .logo {
    font-family: var(--font-display);
    font-size: 24px;
    font-weight: 600;
    color: #FBF8F1;
  }
}
.drawer-inner :deep(.el-menu) {
  border-right: none;
  padding: 16px 12px;
  background-color: transparent;
  flex: 1;
}
.drawer-inner :deep(.el-menu-item) {
  height: 46px;
  line-height: 46px;
  border-radius: var(--radius-button);
  margin-bottom: 2px;
  color: rgba(251, 248, 241, 0.7);
  background-color: transparent;
  position: relative;
  transition: var(--transition-fast);
  font-family: var(--font-serif);
  &:hover {
    background: rgba(251, 248, 241, 0.05);
    color: #FBF8F1;
  }
  &.is-active {
    background: rgba(122, 31, 43, 0.18);
    color: var(--color-accent-soft);
    font-weight: 500;
    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 60%;
      background: var(--color-accent);
      animation: menuBarIn 0.3s var(--ease-out) both;
    }
  }
}
.drawer-inner .aside-footer {
  margin-top: 0;
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .header {
    padding: 0 16px;
    height: 56px;
  }
  .header-left {
    gap: 8px;
  }
  .masthead-tag {
    display: none;
  }
  .logo {
    font-size: 20px;
  }
  .main {
    padding: 12px;
  }
}
</style>
