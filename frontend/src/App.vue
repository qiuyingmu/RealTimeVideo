<template>
  <div id="app-root">
    <!-- 已登录用户的导航栏（不在移动端路由显示，MobileLayout自带导航） -->
    <nav v-if="authStore.isAuthenticated && !isMobileRoute" class="navbar">
      <div class="navbar-left">
        <router-link to="/" class="navbar-brand">
          <img src="/logo.svg" class="brand-icon" alt="logo" />
          <span class="brand-text">视频监控平台</span>
        </router-link>
      </div>
      <div class="navbar-right">
        <router-link to="/" class="nav-link">设备列表</router-link>
        <router-link v-if="authStore.isAdmin" to="/admin/users" class="nav-link">用户管理</router-link>
        <router-link v-if="authStore.isAdmin" to="/admin/permissions" class="nav-link">设备权限</router-link>
        <router-link v-if="authStore.isAdmin" to="/admin/logs" class="nav-link">操作日志</router-link>
        <div class="user-info">
          <span class="username">{{ authStore.displayName || authStore.username }}</span>
          <span v-if="authStore.isAdmin" class="role-badge admin">管理员</span>
          <span v-else class="role-badge user">用户</span>
        </div>
        <router-link to="/change-password" class="nav-link btn-change-pwd">修改密码</router-link>
        <button class="btn-logout" @click="handleLogout">退出登录</button>
      </div>
    </nav>

    <!-- 主内容区域 -->
    <main :class="{ 'with-nav': authStore.isAuthenticated && !isMobileRoute, 'mobile-route': isMobileRoute }">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 全局 Toast / 确认弹窗 -->
    <GlobalToast />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import { useRouter, useRoute } from 'vue-router'
import GlobalToast from '@/components/GlobalToast.vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const isMobileRoute = computed(() => route.path.startsWith('/mobile'))

// 挂载时应用已保存的夜间模式（全局生效）
onMounted(() => {
  if (localStorage.getItem('mobileDarkMode') === 'true') {
    document.documentElement.classList.add('dark')
  }
})

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  box-shadow: var(--shadow);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text);
}

.brand-icon {
  height: 32px;
  width: auto;
}

.brand-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--primary);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.nav-link {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-link:hover,
.nav-link.router-link-exact-active {
  color: var(--primary);
  background: rgba(26, 115, 232, 0.08);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-left: 1px solid var(--border);
}

.username {
  font-size: 14px;
  font-weight: 500;
}

.role-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.role-badge.admin {
  background: #e8f5e9;
  color: #2e7d32;
}

.role-badge.user {
  background: #e3f2fd;
  color: #1565c0;
}

.btn-logout {
  padding: 6px 16px;
  font-size: 13px;
  border: 1px solid var(--danger);
  background: transparent;
  color: var(--danger);
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-logout:hover {
  background: var(--danger);
  color: #fff;
}

main {
  min-height: calc(100vh - 64px);
}

main.with-nav {
  padding: 24px;
}

main.mobile-route {
  padding: 0;
  min-height: 100vh;
}

/* ====== 移动端响应式 ====== */
@media (max-width: 768px) {
  .navbar {
    padding: 0 12px;
    height: 56px;
    z-index: 160; /* 高于sidebar-overlay，低于sidebar */
  }

  .brand-text {
    font-size: 15px;
  }

  .brand-icon {
    width: auto;
    height: 26px;
  }

  .navbar-right {
    gap: 8px;
  }

  .nav-link {
    font-size: 12px;
    padding: 4px 8px;
  }

  .user-info {
    padding: 0 8px;
    gap: 4px;
    border-left: none;
  }

  .username {
    display: none; /* 移动端隐藏用户名 */
  }

  .role-badge {
    font-size: 10px;
  }

  .btn-logout {
    padding: 4px 10px;
    font-size: 12px;
  }

  main {
    min-height: calc(100vh - 56px);
  }

  main.with-nav {
    padding: 16px;
  }
}
</style>

<!-- 全屏/横屏全局样式（非scoped，作用于body） -->
<style>
/* 全屏模式：隐藏导航栏，body无滚动 */
body.video-fullscreen {
  overflow: hidden;
}
body.video-fullscreen .navbar {
  display: none;
}
body.video-fullscreen main.with-nav {
  padding: 0;
  min-height: 100vh;
}

/* 移动端横屏：隐藏导航栏，视频占满全屏 */
@media (max-width: 768px) and (orientation: landscape) {
  body.video-landscape .navbar {
    display: none;
  }
  body.video-landscape main.with-nav {
    padding: 0;
    min-height: 100vh;
  }
}
</style>
