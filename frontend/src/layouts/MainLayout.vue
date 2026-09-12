<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'
import http from '@/api/request'
import VersionUpdateDialog from '@/components/common/VersionUpdateDialog.vue'
import { APP_VERSION } from '@/utils/version'
import { warmUpBackend } from '@/utils/warmup'
import {
  ChatDotRound,
  Monitor,
  Document,
  Files,
  Search,
  List,
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
  // 登录进入主界面即后台预热后端，把 free 冷启动转到浏览/输入时段，减少首问等待
  warmUpBackend()
})

onUnmounted(() => {
  mediaQuery?.removeEventListener('change', updateMobile)
})

// 公报式菜单：带罗马数字章节编号
// 审计日志仅 ADMIN 可见（v1.8.0 新增）
const allMenus = [
  { index: '/chat', title: '智能问答', icon: ChatDotRound, numeral: 'I', requireAdmin: false },
  { index: '/documents', title: '文档分析', icon: Document, numeral: 'II', requireAdmin: false },
  { index: '/templates', title: '文书模板', icon: Files, numeral: 'III', requireAdmin: false },
  { index: '/cases', title: '案例检索', icon: Search, numeral: 'IV', requireAdmin: false },
  { index: '/health', title: '系统状态', icon: Monitor, numeral: 'V', requireAdmin: false },
  { index: '/audit', title: '审计日志', icon: List, numeral: 'VI', requireAdmin: true },
]

const menus = computed(() =>
  allMenus.filter((m) => !m.requireAdmin || userStore.role === 'ADMIN')
)

function handleMenuSelect(index: string) {
  router.push(index)
  if (isMobile.value) drawerVisible.value = false
}

// v1.11.0 修复 H-2：登出时调用后端 /auth/logout 将 access/refresh token 加入黑名单，
// 避免登出后 token 在有效期内被窃取仍可使用。best-effort 调用：失败也清本地状态。
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  try {
    // v1.16：用 raw(底层 axios) 登出，绕过响应拦截器——后端登出失败(如 Redis 抖动)也不弹错误 toast，
    // 登出以本地清理+跳转为准，best-effort 调用后端黑名单。
    // v1.17：在清 store 之前带上 refreshToken，使服务端一并吊销（否则被盗 refresh token 仍可换新 token）。
    await http.raw.post('/auth/logout', { refreshToken: userStore.refreshToken })
  } catch {
    // best-effort：后端登出失败不阻塞前端清理
  }
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
          <div class="version">linzAI v{{ APP_VERSION }} · The Verdict</div>
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
          <div class="version">linzAI v{{ APP_VERSION }} · The Verdict</div>
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
  // 液态玻璃深墨顶栏：酒红/古铜角落光晕
  background:
    radial-gradient(90% 150% at 100% 0%, rgba(122, 31, 43, 0.30), transparent 55%),
    radial-gradient(70% 170% at 0% 100%, rgba(154, 107, 47, 0.18), transparent 60%),
    linear-gradient(180deg, #201a16 0%, #14110f 100%);
  border-bottom: 1px solid rgba(154, 107, 47, 0.30);
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

// ===== 侧边栏：深墨玻璃面板（古铜/酒红光晕） =====
.aside {
  background:
    radial-gradient(120% 90% at 0% 0%, rgba(154, 107, 47, 0.14), transparent 60%),
    radial-gradient(140% 100% at 100% 100%, rgba(122, 31, 43, 0.20), transparent 60%),
    var(--color-primary);
  border-right: 1px solid rgba(154, 107, 47, 0.16);
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
  background-color: transparent; // 透出全局液态玻璃背景
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
  background:
    radial-gradient(120% 90% at 0% 0%, rgba(154, 107, 47, 0.14), transparent 60%),
    radial-gradient(140% 100% at 100% 100%, rgba(122, 31, 43, 0.20), transparent 60%),
    var(--color-primary);
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

/* 超小屏手机：进一步压缩间距和字号 */
@media (max-width: 480px) {
  .header {
    padding: 0 12px;
    height: 52px;
  }
  .masthead-eyebrow {
    display: none;
  }
  .logo {
    font-size: 18px;
  }
  .header-right .user-info {
    padding: 4px 8px;
  }
  .username {
    font-size: 12px;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .main {
    padding: 10px;
  }
}
</style>
