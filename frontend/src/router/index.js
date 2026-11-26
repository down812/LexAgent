import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/consult',
    children: [
      {
        path: 'consult',
        name: 'Consult',
        component: () => import('@/views/Consult.vue'),
        meta: { title: '法律智能问诊', icon: 'ChatDotRound' }
      },
      {
        path: 'cases',
        name: 'CaseList',
        component: () => import('@/views/CaseList.vue'),
        meta: { title: '案件管理', icon: 'Briefcase' }
      },
      {
        path: 'cases/:id',
        name: 'CaseDetail',
        component: () => import('@/views/CaseDetail.vue'),
        meta: { title: '案件详情', icon: 'Briefcase' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/Knowledge.vue'),
        meta: { title: '知识库管理', icon: 'FolderOpened', requireAdmin: true }
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/Search.vue'),
        meta: { title: '检索测试', icon: 'Search' }
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/Admin.vue'),
        meta: { title: '子账号管理', icon: 'UserFilled', requireSuperAdmin: true }
      },
      {
        path: 'feedback-stats',
        name: 'FeedbackStats',
        component: () => import('@/views/FeedbackStats.vue'),
        meta: { title: '评价统计', icon: 'DataAnalysis', requireAdmin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/consult'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  document.title = to.meta.title
    ? `${to.meta.title} · LexAgent`
    : 'LexAgent · 智能法律援助系统'

  const userStore = useUserStore()
  const isLogged = userStore.isLoggedIn

  if (to.path === '/login' || to.path === '/register') {
    next(isLogged ? '/consult' : undefined)
    return
  }

  if (!isLogged) {
    next('/login')
    return
  }

  if (to.meta.requireSuperAdmin && !userStore.isSuperAdmin) {
    next('/consult')
    return
  }
  if (to.meta.requireAdmin && !userStore.isAdmin) {
    next('/consult')
    return
  }

  next()
})

export default router
