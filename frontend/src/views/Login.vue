<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header" @click="handleHeaderClick">
        <img src="/logo.svg" class="login-logo" alt="logo" />
        <h1 class="login-title">贵州建工监理咨询有限公司</h1>
        <p class="login-subtitle project-name">数字化管理平台 项目监控系统</p>
        <p class="login-hint">如需开通权限，请联系项目人员</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin" autocomplete="off">
        <!-- 手机号（两种模式共用） -->
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
              maxlength="11"
              :disabled="loading"
              autocomplete="username"
              @input="clearError"
            />
          </div>
          <small v-if="loginMode === 'sms'" class="field-hint">请输入管理员授权的手机号</small>
        </div>

        <!-- 密码登录模式（管理员入口） -->
        <template v-if="loginMode === 'password'">
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
              <button type="button" class="toggle-password" @click="showPassword = !showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'" tabindex="-1">
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
        </template>

        <!-- 验证码登录模式（用户默认） -->
        <template v-if="loginMode === 'sms'">
          <div class="form-group">
            <label for="smsCode">验证码</label>
            <div class="input-wrapper code-wrapper">
              <svg class="input-icon" viewBox="0 0 24 24" width="20" height="20">
                <rect x="3" y="11" width="18" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4" fill="none" stroke="currentColor" stroke-width="2"/>
              </svg>
              <input
                id="smsCode"
                v-model="form.smsCode"
                type="text"
                placeholder="请输入验证码"
                maxlength="4"
                :disabled="loading"
                autocomplete="one-time-code"
                @input="clearError"
              />
              <button type="button" class="btn-get-code" :disabled="codeSending || codeCountdown > 0 || !form.username.trim()" @click="sendSmsCode">
                {{ codeCountdown > 0 ? `${codeCountdown}s` : (codeSending ? '发送中...' : '获取验证码') }}
              </button>
            </div>
          </div>
        </template>

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
          <span v-if="!loading">{{ loginMode === 'password' ? '管理员登录' : '登 录' }}</span>
          <span v-else class="loading-spinner"></span>
        </button>

        <!-- 管理员密码模式下的返回链接 -->
        <div v-if="loginMode === 'password'" class="admin-entry">
          <button type="button" class="admin-entry-btn back" @click="loginMode = 'sms'">← 返回</button>
        </div>
      </form>

      <div class="login-footer">
        <p>© 2026 贵州建工监理咨询有限公司</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { authApi } from '@/api'

function isMobileDevice() {
  return window.innerWidth <= 768 || 'ontouchstart' in window
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginMode = ref('sms') // 'password' | 'sms'
let headerClickCount = 0
let headerClickTimer = null
const form = reactive({
  username: '',
  password: '',
  smsCode: ''
})

const loading = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')
const codeSending = ref(false)
const codeCountdown = ref(0)
let codeTimer = null

const isFormValid = computed(() => {
  if (loginMode.value === 'password') {
    return form.username.trim().length >= 3 && form.password.length >= 6
  }
  return form.username.trim().length >= 11 && form.smsCode.length >= 4
})

function clearError() {
  errorMessage.value = ''
}

async function sendSmsCode() {
  const phone = form.username.trim()
  if (!phone || phone.length < 11 || codeSending.value || codeCountdown.value > 0) return
  codeSending.value = true
  errorMessage.value = ''
  try {
    await authApi.sendSmsCode(phone)
    startCodeCountdown()
  } catch (err) {
    errorMessage.value = err.friendlyMessage || '验证码发送失败'
  } finally {
    codeSending.value = false
  }
}

function startCodeCountdown() {
  codeCountdown.value = 60
  if (codeTimer) clearInterval(codeTimer)
  codeTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(codeTimer)
      codeTimer = null
    }
  }, 1000)
}

// 连续点击 logo 区域 3 次进入管理员密码登录
function handleHeaderClick() {
  headerClickCount++
  if (headerClickTimer) clearTimeout(headerClickTimer)
  headerClickTimer = setTimeout(() => { headerClickCount = 0 }, 800)
  if (headerClickCount >= 3) {
    headerClickCount = 0
    loginMode.value = 'password'
  }
}

async function handleLogin() {
  if (!isFormValid.value || loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    if (loginMode.value === 'sms') {
      await authStore.smsLogin(form.username.trim(), form.smsCode)
      // 短信登录后按设备跳转（普通用户和管理员都适用）
      const redirect = route.query.redirect || (isMobileDevice() ? '/mobile/dashboard' : '/')
      router.push(redirect)
    } else {
      await authStore.login(form.username.trim(), form.password)
      const redirect = route.query.redirect || (isMobileDevice() ? '/mobile/dashboard' : '/')
      router.push(redirect)
    }
  } catch (error) {
    errorMessage.value = error.friendlyMessage || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (codeTimer) clearInterval(codeTimer)
})
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

/* 管理员返回链接 */
.admin-entry {
  text-align: center;
  margin-top: -4px;
}

.admin-entry-btn.back {
  background: none;
  border: none;
  font-size: 11px;
  color: var(--text-secondary);
  cursor: pointer;
  opacity: 0.35;
  transition: opacity 0.2s;
  padding: 2px 6px;
}
.admin-entry-btn.back:hover {
  opacity: 0.6;
}

/* 验证码输入行 */
.code-wrapper {
  display: flex;
  gap: 8px;
}

.code-wrapper input {
  flex: 1;
}

.btn-get-code {
  flex-shrink: 0;
  padding: 0 14px;
  height: 48px;
  font-size: 13px;
  font-weight: 600;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-get-code:hover:not(:disabled) {
  background: var(--primary-dark);
}

.btn-get-code:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

/* 移动端适配 — 全屏白底 + 顶部渐变装饰条 */
@media (max-width: 768px) {
  .login-page {
    padding: 0;
    align-items: stretch;
    background: #fff;
    min-height: 100dvh;
  }

  .login-container {
    max-width: 100%;
    border-radius: 0;
    padding: 0;
    box-shadow: none;
    display: flex;
    flex-direction: column;
    min-height: 100dvh;
    animation: none;
    position: relative;
  }

  /* 顶部渐变装饰条 */
  .login-container::before {
    content: '';
    display: block;
    height: 140px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    flex-shrink: 0;
  }

  .login-header {
    margin: -70px 20px 0;
    padding: 24px 16px 20px;
    background: #fff;
    border-radius: 16px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.08);
    position: relative;
    z-index: 1;
  }

  .login-logo {
    width: 52px;
    height: 52px;
    margin-bottom: 12px;
  }

  .login-title {
    font-size: 20px;
    margin-bottom: 4px;
  }

  .login-subtitle {
    font-size: 13px;
  }

  .login-subtitle.project-name {
    font-size: 14px;
  }

  .login-hint {
    font-size: 12px;
    margin-top: 8px;
  }

  .login-form {
    padding: 20px 20px 0;
    gap: 16px;
    flex: 1;
  }

  .login-mode-tabs {
    margin-bottom: 4px;
  }

  .input-wrapper {
    margin-bottom: 0;
  }

  .input-wrapper input {
    padding: 14px 16px;
    font-size: 16px;
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

  .login-footer {
    padding: 16px 20px 24px;
    margin-top: auto;
  }

  .login-footer p {
    font-size: 11px;
  }

  /* 设置密码弹窗移动端适配 */
  
}
</style>
