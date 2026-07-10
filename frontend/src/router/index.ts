import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound' },
      },
      {
        path: 'health',
        name: 'Health',
        component: () => import('@/views/HealthView.vue'),
        meta: { title: '系统状态', icon: 'Monitor' },
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/DocumentView.vue'),
        meta: { title: '文档分析', icon: 'Document' },
      },
      {
        path: 'templates',
        name: 'Templates',
        component: () => import('@/views/TemplateView.vue'),
        meta: { title: '文书模板', icon: 'Files' },
      },
      {
        path: 'cases',
        name: 'Cases',
        component: () => import('@/views/CaseView.vue'),
        meta: { title: '案例检索', icon: 'Search' },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/privacy',
    name: 'Privacy',
    component: () => import('@/views/PrivacyView.vue'),
    meta: { title: '隐私政策', public: true },
  },
  {
    path: '/terms',
    name: 'Terms',
    component: () => import('@/views/TermsView.vue'),
    meta: { title: '用户协议', public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, _from, next) => {
  document.body.style.cursor = 'wait'
  const userStore = useUserStore()
  document.title = `${to.meta.title || ''} - AI 法律助手`
  if (to.meta.public || userStore.isLogin) {
    next()
  } else {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  }
})

// 路由后置钩子：恢复光标
router.afterEach(() => {
  document.body.style.cursor = 'default'
})

export default router
