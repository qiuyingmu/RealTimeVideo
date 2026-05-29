import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

// 移动端设备检测
function isMobileDevice() {
  if (typeof window === 'undefined') return false
  return window.innerWidth <= 768 || 'ontouchstart' in window
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/ChangePassword.vue'),
    meta: { requiresAuth: true, allowWhileRequiringChange: true }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  // ---- 移动端路由（嵌套在 MobileLayout 布局下） ----
  {
    path: '/mobile',
    component: () => import('@/views/MobileLayout.vue'),
    meta: { requiresAuth: true, mobileLayout: true },
    children: [
      { path: '', redirect: { name: 'MobileDashboard' } },
      {
        path: 'dashboard',
        name: 'MobileDashboard',
        component: () => import('@/views/MobileDashboard.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'play',
        name: 'MobilePlay',
        component: () => import('@/views/MobilePlay.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'settings',
        name: 'MobileSettings',
        component: () => import('@/views/MobileSettings.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'settings/change-password',
        name: 'MobileChangePassword',
        component: () => import('@/views/ChangePassword.vue'),
        meta: { requiresAuth: true }
      },
      // ---- 移动端管理后台（管理员可见） ----
      {
        path: 'admin/users',
        name: 'MobileAdminUsers',
        component: () => import('@/views/AdminUsers.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/permissions',
        name: 'MobileAdminPermissions',
        component: () => import('@/views/AdminPermissions.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/logs',
        name: 'MobileAdminLogs',
        component: () => import('@/views/AdminLogs.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/AdminUsers.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/permissions',
    name: 'AdminPermissions',
    component: () => import('@/views/AdminPermissions.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/logs',
    name: 'AdminLogs',
    component: () => import('@/views/AdminLogs.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 - 认证检查 + 移动端重定向 + 强制改密
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 需要认证的页面
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  // 已登录用户访问登录页，重定向到首页
  if (to.name === 'Login' && authStore.isAuthenticated) {
    return next(isMobileDevice() ? { name: 'MobileDashboard' } : { name: 'Dashboard' })
  }

  // 强制修改密码（首次登录）：除改密页和登录页外，全部拦截
  if (authStore.passwordChangeRequired &&
      to.meta.requiresAuth &&
      !to.meta.allowWhileRequiringChange &&
      to.name !== 'ChangePassword' &&
      to.name !== 'MobileChangePassword') {
    return next({ name: 'ChangePassword' })
  }

  // 需要管理员权限的页面
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return next(isMobileDevice() ? { name: 'MobileDashboard' } : { name: 'Dashboard' })
  }

  // 移动端访问桌面端首页 → 重定向到移动端首页
  if (to.name === 'Dashboard' && isMobileDevice()) {
    return next({ name: 'MobileDashboard' })
  }

  // 桌面端访问移动端页面 → 重定向到桌面端首页
  if (to.meta.mobileLayout && !isMobileDevice()) {
    return next({ name: 'Dashboard' })
  }

  next()
})

export default router
