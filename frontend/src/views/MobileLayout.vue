<template>
  <div class="mobile-layout">
    <!-- 顶部状态栏 -->
    <header class="mobile-header">
      <div class="mobile-header-left">
        <slot name="header-left">
          <h1 class="mobile-title">视频监控平台</h1>
        </slot>
      </div>
      <div class="mobile-header-right">
        <slot name="header-right">
          <button class="mobile-header-btn" @click="handleLogout" aria-label="退出登录">
            <svg viewBox="0 0 24 24" width="20" height="20">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <polyline points="16 17 21 12 16 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <line x1="21" y1="12" x2="9" y2="12" stroke="currentColor" stroke-width="2"/>
            </svg>
          </button>
        </slot>
      </div>
    </header>

    <!-- 页面内容区域 -->
    <main class="mobile-content">
      <router-view />
    </main>

    <!-- 底部导航栏 -->
    <nav class="mobile-tabbar">
      <router-link to="/mobile/dashboard" class="tab-item" :class="{ active: $route.path === '/mobile/dashboard' || $route.path === '/mobile' }">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <rect x="3" y="3" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="2"/>
          <rect x="14" y="3" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="2"/>
          <rect x="3" y="14" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="2"/>
          <rect x="14" y="14" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
        <span class="tab-label">设备</span>
      </router-link>
      <router-link to="/mobile/play" class="tab-item" :class="{ active: $route.path === '/mobile/play' }">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <polygon points="5 3 19 12 5 21 5 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="tab-label">播放</span>
      </router-link>
      <router-link to="/mobile/settings" class="tab-item" :class="{ active: $route.path === '/mobile/settings' }">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
          <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
          <line x1="12" y1="1" x2="12" y2="4" stroke="currentColor" stroke-width="2"/>
          <line x1="12" y1="20" x2="12" y2="23" stroke="currentColor" stroke-width="2"/>
          <line x1="1" y1="12" x2="4" y2="12" stroke="currentColor" stroke-width="2"/>
          <line x1="20" y1="12" x2="23" y2="12" stroke="currentColor" stroke-width="2"/>
        </svg>
        <span class="tab-label">设置</span>
      </router-link>
      <!-- 管理后台 Tab（仅管理员可见） -->
      <router-link
        v-if="authStore.isAdmin"
        to="/mobile/admin/users"
        class="tab-item"
        :class="{ active: $route.path.startsWith('/mobile/admin') }"
      >
        <svg viewBox="0 0 24 24" width="22" height="22">
          <rect x="3" y="3" width="18" height="18" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
          <line x1="12" y1="8" x2="12" y2="16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <line x1="8" y1="12" x2="16" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <span class="tab-label">管理</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
  overflow: hidden;
}

/* 顶部栏 */
.mobile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
  z-index: 10;
}

.mobile-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.mobile-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a73e8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mobile-header-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 50%;
  color: #64748b;
  cursor: pointer;
  transition: background 0.2s;
}

.mobile-header-btn:active {
  background: #f1f5f9;
  color: #1a1a2e;
}

/* 内容区域 */
.mobile-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

/* 底部导航 */
.mobile-tabbar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 56px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
  z-index: 10;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 4px 16px;
  text-decoration: none;
  color: #94a3b8;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}

.tab-item.active {
  color: #1a73e8;
}

.tab-item:active {
  opacity: 0.7;
}

.tab-label {
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}
</style>
