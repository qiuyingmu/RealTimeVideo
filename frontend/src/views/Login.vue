<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <img src="/logo.svg" class="login-logo" alt="logo" />
        <h1 class="login-title">贵州建工监理咨询有限公司</h1>
        <p class="login-subtitle">数字化管理平台</p>
        <p class="login-subtitle project-name">项目监控系统</p>
        <p class="login-hint">如需开通权限，请联系项目人员</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin" autocomplete="off">
        <!-- 手机号 -->
        <div class="form-group">
          <label for="username">手机号</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" width="20" height="20">
              <circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="2"/>
              <path d="M4 22c0-4.418 3.582-8 8-8s8 3.582 8 8" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
            <input
              id="username"
              v-model="form.username"
              type="text"
              placeholder="请输入手机号"
              maxlength="50"
              :disabled="loading"
              autocomplete="username"
              @input="clearError"
            />
          </div>
        </div>

        <!-- 密码 -->
        <div class="form-group">
          <label for="password">密码</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" width="20" height="20">
              <rect x="3" y="11" width="18" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
              <path d="M7 11V7a5 5 0 0110 0v4" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              maxlength="100"
              :disabled="loading"
              autocomplete="current-password"
              @input="clearError"
            />
            <button
              type="button"
              class="toggle-password"
              @click="showPassword = !showPassword"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              tabindex="-1"
            >
              <svg v-if="!showPassword" viewBox="0 0 24 24" width="18" height="18">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" fill="none" stroke="currentColor" stroke-width="2"/>
                <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" width="18" height="18">
                <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94" fill="none" stroke="currentColor" stroke-width="2"/>
                <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.06 3.06" fill="none" stroke="currentColor" stroke-width="2"/>
                <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" stroke-width="2"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 错误提示 -->
        <transition name="fade">
          <div v-if="errorMessage" class="error-message">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
              <line x1="12" y1="8" x2="12" y2="13" stroke="currentColor" stroke-width="2"/>
              <circle cx="12" cy="16" r="1" fill="currentColor"/>
            </svg>
            <span>{{ errorMessage }}</span>
          </div>
        </transition>

        <!-- 登录按钮 -->
        <button type="submit" class="btn-login" :disabled="loading || !isFormValid">
          <span v-if="!loading">登 录</span>
          <span v-else class="loading-spinner"></span>
        </button>
      </form>

      <div class="login-footer">
        <p>© 2026 贵州建工监理咨询有限公司</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'

// 移动端检测
function isMobileDevice() {
  return window.innerWidth <= 768 || 'ontouchstart' in window
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')

const isFormValid = computed(() => {
  return form.username.trim().length >= 3 && form.password.length >= 6
})

function clearError() {
  errorMessage.value = ''
}

async function handleLogin() {
  if (!isFormValid.value || loading.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    await authStore.login(form.username.trim(), form.password)
    const redirect = route.query.redirect || (isMobileDevice() ? '/mobile/dashboard' : '/')
    router.push(redirect)
  } catch (error) {
    errorMessage.value = error.friendlyMessage || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: fadeIn 0.5s ease;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.login-subtitle.project-name {
  color: #dc2626;
  font-weight: 700;
  font-size: 16px;
  margin-top: -4px;
}

.login-hint {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 12px;
  text-align: center;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 12px;
  color: var(--text-secondary);
  pointer-events: none;
}

.input-wrapper input {
  width: 100%;
  padding: 12px 12px 12px 42px;
  font-size: 15px;
  border: 2px solid var(--border);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fafafa;
}

.input-wrapper input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(26, 115, 232, 0.12);
  background: #fff;
}

.input-wrapper input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.toggle-password {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 4px;
  display: flex;
  align-items: center;
}

.toggle-password:hover {
  color: var(--text);
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff0f0;
  border: 1px solid #ffcdd2;
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 13px;
}

.btn-login {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  font-weight: 600;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
}

.btn-login:hover:not(:disabled) {
  background: var(--primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(26, 115, 232, 0.3);
}

.btn-login:active:not(:disabled) {
  transform: translateY(0);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 22px;
  height: 22px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
}

.login-footer p {
  font-size: 12px;
  color: var(--text-secondary);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .login-page {
    padding: 0;
    align-items: flex-start;
    background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  }

  .login-container {
    max-width: 100%;
    border-radius: 16px 16px 0 0;
    margin-top: 80px;
    padding: 32px 24px;
    padding-bottom: 40px;
  }

  .login-header svg {
    width: 56px;
    height: 56px;
  }

  .login-header h1 {
    font-size: 22px;
  }

  .login-header p {
    font-size: 13px;
  }

  .input-wrapper {
    margin-bottom: 18px;
  }

  .input-wrapper input {
    padding: 14px 16px;
    font-size: 16px; /* 防止iOS缩放 */
    padding-left: 44px;
  }

  .input-icon {
    left: 14px;
  }

  .btn-login {
    padding: 16px;
    font-size: 16px;
  }

  .toggle-password {
    right: 14px;
    padding: 8px;
  }
}
</style>
