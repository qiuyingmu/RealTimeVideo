<template>
  <div class="mobile-settings">
    <!-- 用户信息卡片 -->
    <div class="settings-card user-card">
      <div class="user-avatar">
        <svg viewBox="0 0 24 24" width="28" height="28">
          <circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="2"/>
          <path d="M4 22c0-4.418 3.582-8 8-8s8 3.582 8 8" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
      </div>
      <div class="user-info">
        <span class="user-name">{{ authStore.displayName || authStore.username }}</span>
        <span class="user-role" :class="authStore.isAdmin ? 'admin' : 'user'">
          {{ authStore.isAdmin ? '管理员' : '普通用户' }}
        </span>
      </div>
    </div>

    <!-- 设置选项列表 -->
    <div class="settings-section">
      <div class="settings-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
            <path d="M12 8v4l3 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>自动刷新</span>
        </div>
        <label class="toggle-switch">
          <input type="checkbox" v-model="autoRefresh" @change="toggleAutoRefresh" />
          <span class="toggle-slider"></span>
        </label>
      </div>

      <div class="settings-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>夜间模式</span>
        </div>
        <label class="toggle-switch">
          <input type="checkbox" v-model="darkMode" @change="toggleDarkMode" />
          <span class="toggle-slider"></span>
        </label>
      </div>

      <div class="settings-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>画质</span>
        </div>
        <select v-model="videoQuality" class="settings-select" @change="changeQuality">
          <option value="hd">高清</option>
          <option value="sd">标清</option>
          <option value="fluent">流畅</option>
        </select>
      </div>
    </div>

    <!-- 账户安全（仅管理员可见） -->
    <div v-if="authStore.isAdmin" class="settings-section">
      <div class="section-header">账户安全</div>
      <router-link to="/mobile/settings/change-password" class="settings-item link-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <rect x="3" y="11" width="18" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4" fill="none" stroke="currentColor" stroke-width="2"/>
            <circle cx="12" cy="16" r="1" fill="currentColor"/>
          </svg>
          <span>修改密码</span>
        </div>
        <svg viewBox="0 0 24 24" width="16" height="16" class="chevron">
          <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
      </router-link>
    </div>

    <!-- 后台管理（仅管理员可见） -->
    <div v-if="authStore.isAdmin" class="settings-section">
      <div class="section-header">后台管理</div>
      <router-link to="/mobile/admin/users" class="settings-item link-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <circle cx="9" cy="7" r="4" fill="none" stroke="currentColor" stroke-width="2"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>用户管理</span>
        </div>
        <svg viewBox="0 0 24 24" width="16" height="16" class="chevron">
          <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
      </router-link>
      <router-link to="/mobile/admin/permissions" class="settings-item link-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <rect x="3" y="11" width="18" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4" fill="none" stroke="currentColor" stroke-width="2"/>
          </svg>
          <span>权限管理</span>
        </div>
        <svg viewBox="0 0 24 24" width="16" height="16" class="chevron">
          <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
      </router-link>
      <router-link to="/mobile/admin/logs" class="settings-item link-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" fill="none" stroke="currentColor" stroke-width="2"/>
            <polyline points="14 2 14 8 20 8" fill="none" stroke="currentColor" stroke-width="2"/>
            <line x1="16" y1="13" x2="8" y2="13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <line x1="16" y1="17" x2="8" y2="17" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>日志管理</span>
        </div>
        <svg viewBox="0 0 24 24" width="16" height="16" class="chevron">
          <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
        </svg>
      </router-link>
    </div>

    <!-- 关于 -->
    <div class="settings-section">
      <div class="settings-item info-item">
        <div class="settings-item-left">
          <svg viewBox="0 0 24 24" width="20" height="20" class="settings-icon">
            <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
            <line x1="12" y1="16" x2="12" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <line x1="12" y1="8" x2="12.01" y2="8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>版本</span>
        </div>
        <span class="info-value">v2.0.0</span>
      </div>
    </div>

    <!-- 退出登录 -->
    <button class="logout-btn" @click="handleLogout">
      <svg viewBox="0 0 24 24" width="20" height="20">
        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        <polyline points="16 17 21 12 16 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        <line x1="21" y1="12" x2="9" y2="12" stroke="currentColor" stroke-width="2"/>
      </svg>
      退出登录
    </button>

    <p class="copyright">© 2026 贵州建工监理咨询有限公司</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

const autoRefresh = ref(localStorage.getItem('mobileAutoRefresh') !== 'false')
const darkMode = ref(localStorage.getItem('mobileDarkMode') === 'true')
const videoQuality = ref(localStorage.getItem('videoQuality') || 'fluent')

// 挂载时应用已保存的夜间模式
onMounted(() => {
  if (darkMode.value) {
    document.documentElement.classList.add('dark')
  }
})

function toggleAutoRefresh() {
  localStorage.setItem('mobileAutoRefresh', autoRefresh.value)
}

function toggleDarkMode() {
  localStorage.setItem('mobileDarkMode', darkMode.value)
  document.documentElement.classList.toggle('dark', darkMode.value)
}

function changeQuality() {
  localStorage.setItem('videoQuality', videoQuality.value)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.mobile-settings {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 用户信息卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  align-self: flex-start;
}

.user-role.admin {
  background: #e8f5e9;
  color: #2e7d32;
}

.user-role.user {
  background: #e3f2fd;
  color: #1565c0;
}

/* 设置区块 */
.settings-section {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  margin-bottom: 16px;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  min-height: 48px;
}

.settings-item + .settings-item {
  border-top: 1px solid #f1f5f9;
}

.settings-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #1a1a2e;
  flex: 1;
  min-width: 0;
}

.settings-item-left span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-icon {
  color: #64748b;
  flex-shrink: 0;
}

/* 开关 */
.toggle-switch {
  position: relative;
  width: 46px;
  height: 26px;
  flex-shrink: 0;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #cbd5e1;
  border-radius: 26px;
  transition: all 0.3s;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 20px;
  width: 20px;
  left: 3px;
  bottom: 3px;
  background: #fff;
  border-radius: 50%;
  transition: all 0.3s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.toggle-switch input:checked + .toggle-slider {
  background: #1a73e8;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

/* 下拉选择 */
.settings-select {
  padding: 6px 28px 6px 12px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #1a1a2e;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  outline: none;
}

.settings-select:focus {
  border-color: #1a73e8;
}

/* 纯信息项 */
.info-value {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

/* 后台管理区标题 */
.section-header {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 12px 16px 8px;
}

/* 链接项 */
.link-item {
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition: background 0.2s;
}

.link-item:active {
  background: #f1f5f9;
}

.link-item .chevron {
  color: #cbd5e1;
  flex-shrink: 0;
}

html.dark .link-item:active {
  background: rgba(255,255,255,0.05);
}

html.dark .section-header {
  color: var(--text-secondary);
}

/* 退出按钮 */
.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #fca5a5;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #dc2626;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}

.logout-btn:active {
  background: #fef2f2;
  transform: scale(0.98);
}

.copyright {
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
  padding: 12px 0 24px;
}

/* 移动端小屏适配 */
@media (max-width: 400px) {
  .mobile-settings { padding: 10px 12px; }
  .settings-item { padding: 12px; min-height: 44px; }
  .settings-item-left { gap: 10px; font-size: 13px; }
  .settings-icon { width: 18px; height: 18px; }
  .chevron { width: 14px; height: 14px; }
  .settings-select { font-size: 12px; padding: 5px 24px 5px 10px; }
  .user-card { padding: 12px; gap: 10px; }
  .user-avatar { width: 40px; height: 40px; }
  .user-name { font-size: 14px; }
  .section-header { font-size: 11px; padding: 10px 14px 6px; }
  .link-item { min-height: 44px; }
  .logout-btn { padding: 12px; font-size: 14px; }
}
</style>
