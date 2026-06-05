<template>
  <!-- standalone 模式：全屏渐变背景（用于强制改密重定向） -->
  <!-- inline 模式：无背景，适配 MobileLayout 内嵌 -->
  <div class="change-password-page" :class="{ 'mode-standalone': isStandalone }">
    <div class="cp-container" :class="{ 'mode-standalone': isStandalone }">
      <!-- ====== 成功状态 ====== -->
      <template v-if="success">
        <div class="success-view">
          <div class="success-icon">
            <svg viewBox="0 0 64 64" width="64" height="64">
              <circle cx="32" cy="32" r="30" fill="none" stroke="#10b981" stroke-width="3"/>
              <polyline points="20 32 28 40 44 24" fill="none" stroke="#10b981" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h2 class="success-title">密码修改成功</h2>
          <p class="success-subtitle" v-if="isFirstChange">初始密码已更新，请使用新密码登录</p>
          <p class="success-subtitle" v-else>您的密码已安全更新</p>
          <p class="redirect-hint">即将跳转{{ countdown }}秒...</p>
        </div>
      </template>

      <!-- ====== 密码修改表单 ====== -->
      <template v-else>
        <!-- 头部 -->
        <div class="cp-header" v-if="isStandalone">
          <svg viewBox="0 0 80 80" width="56" height="56">
            <rect x="10" y="38" width="60" height="26" rx="4" fill="none" stroke="var(--primary)" stroke-width="2.5"/>
            <path d="M24 38V24a16 16 0 0 1 32 0v14" fill="none" stroke="var(--primary)" stroke-width="2.5" stroke-linecap="round"/>
            <circle cx="40" cy="53" r="3" fill="var(--danger-color)"/>
            <line x1="40" y1="48" x2="40" y2="56" stroke="var(--danger-color)" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h2 class="cp-title" :class="{ 'mobile-title': !isStandalone }">
          {{ isFirstChange ? '首次登录，请修改密码' : '修改密码' }}
        </h2>
        <p class="cp-subtitle" v-if="isFirstChange && isStandalone">为保障账户安全，首次登录后请立即修改初始密码</p>

        <form class="cp-form" @submit.prevent="handleSubmit">
          <!-- 当前密码 -->
          <div class="form-group">
            <label>当前密码（默认密码 88888888）</label>
            <div class="input-wrapper">
              <input
                v-model="form.oldPassword"
                :type="showOld ? 'text' : 'password'"
                placeholder="输入当前密码"
                maxlength="50"
                :disabled="loading"
                @input="clearError"
              />
              <button type="button" class="toggle-vis" @click="showOld = !showOld" tabindex="-1">
                <svg v-if="!showOld" viewBox="0 0 24 24" width="18" height="18">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" fill="none" stroke="currentColor" stroke-width="2"/>
                  <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="18" height="18">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" fill="none" stroke="currentColor" stroke-width="2"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.06 3.06" fill="none" stroke="currentColor" stroke-width="2"/>
                  <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" stroke-width="2"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 新密码 -->
          <div class="form-group">
            <label>新密码</label>
            <div class="input-wrapper">
              <input
                v-model="form.newPassword"
                :type="showNew ? 'text' : 'password'"
                placeholder="输入新密码（6~18位）"
                maxlength="18"
                :disabled="loading"
                @input="validateForm"
              />
              <button type="button" class="toggle-vis" @click="showNew = !showNew" tabindex="-1">
                <svg v-if="!showNew" viewBox="0 0 24 24" width="18" height="18">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" fill="none" stroke="currentColor" stroke-width="2"/>
                  <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="18" height="18">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" fill="none" stroke="currentColor" stroke-width="2"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.06 3.06" fill="none" stroke="currentColor" stroke-width="2"/>
                  <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" stroke-width="2"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 确认新密码 -->
          <div class="form-group">
            <label>确认新密码</label>
            <div class="input-wrapper">
              <input
                v-model="form.confirmPassword"
                :type="showConfirm ? 'text' : 'password'"
                placeholder="再次输入新密码"
                maxlength="18"
                :disabled="loading"
                @input="validateForm"
              />
              <button type="button" class="toggle-vis" @click="showConfirm = !showConfirm" tabindex="-1">
                <svg v-if="!showConfirm" viewBox="0 0 24 24" width="18" height="18">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" fill="none" stroke="currentColor" stroke-width="2"/>
                  <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="18" height="18">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" fill="none" stroke="currentColor" stroke-width="2"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.06 3.06" fill="none" stroke="currentColor" stroke-width="2"/>
                  <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" stroke-width="2"/>
                </svg>
              </button>
            </div>
            <p v-if="form.confirmPassword && form.newPassword !== form.confirmPassword" class="field-hint error">两次输入的密码不一致</p>
          </div>

          <!-- 密码规则提示 -->
          <div class="password-rules">
            <p class="rule-title">密码规则：</p>
            <ul>
              <li :class="{ valid: form.newPassword.length >= 6 }">至少 6 位</li>
              <li :class="{ valid: form.newPassword.length <= 18 }">不超过 18 位</li>
              <li :class="{ valid: form.newPassword !== '' && form.newPassword === form.confirmPassword }">两次密码输入一致</li>
            </ul>
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

          <!-- 提交按钮 -->
          <button type="submit" class="btn-submit" :disabled="loading || !isFormValid">
            <span v-if="!loading">{{ isFirstChange ? '确认修改' : '保存密码' }}</span>
            <span v-else class="loading-spinner"></span>
          </button>
        </form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { authApi } from '@/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 判断是否为独立页面模式（强制改密重定向） vs 内嵌模式（MobileLayout 内）
const isStandalone = computed(() => {
  return authStore.passwordChangeRequired || route.name === 'ChangePassword'
})

const isFirstChange = computed(() => {
  return authStore.passwordChangeRequired
})

// ====== 表单状态 ======
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)
const errorMessage = ref('')
const success = ref(false)
const countdown = ref(3)
let countdownTimer = null

const isFormValid = computed(() => {
  return form.oldPassword.length >= 1 &&
         form.newPassword.length >= 6 &&
         form.newPassword.length <= 18 &&
         form.newPassword === form.confirmPassword
})

function clearError() {
  errorMessage.value = ''
}

function validateForm() {
  clearError()
}

async function handleSubmit() {
  if (!isFormValid.value || loading.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await authApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })

    const data = response.data.data

    // 更新前端存储的新 Token
    authStore.setTokens(data.accessToken)
    // 改密完成，清除强制改密标记
    authStore.passwordChangeRequired = false

    // 显示成功状态
    success.value = true

    // 倒计时显示（每隔 1 秒更新）
    countdownTimer = setInterval(() => {
      countdown.value--
    }, 1000)

    // 3 秒后用 setTimeout 统一跳转（独立于组件生命周期）
    setTimeout(() => {
      redirectToHome()
    }, 3000)

  } catch (error) {
    errorMessage.value = error.friendlyMessage || '密码修改失败，请重试'
  } finally {
    loading.value = false
  }
}

function redirectToHome() {
  if (window.innerWidth <= 768 || 'ontouchstart' in window) {
    router.replace('/mobile/dashboard')
  } else {
    router.replace('/')
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// 监听路由变化：如果用户在倒计时期间手动跳转（点击导航栏），清理定时器
router.afterEach(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
/* ====== 独立页面模式（全屏渐变背景） ====== */
.change-password-page.mode-standalone {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.cp-container.mode-standalone {
  width: 100%;
  max-width: 440px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: fadeIn 0.4s ease;
}

/* ====== 内嵌模式（MobileLayout 内） ====== */
.change-password-page {
  padding: 16px;
}

.cp-container {
  background: #fff;
  border-radius: 14px;
  padding: 24px 20px;
  border: 1px solid #e2e8f0;
}

/* ====== 公共样式 ====== */
.cp-header {
  text-align: center;
  margin-bottom: 20px;
}

.cp-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 8px;
  text-align: center;
}

.cp-title.mobile-title {
  font-size: 18px;
  margin-bottom: 16px;
}

.cp-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  text-align: center;
  margin-bottom: 20px;
}

.cp-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  width: 100%;
  padding: 11px 44px 11px 14px;
  font-size: 14px;
  border: 2px solid var(--border);
  border-radius: 8px;
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
}

.toggle-vis {
  position: absolute;
  right: 10px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 6px;
  display: flex;
  align-items: center;
}

.field-hint {
  font-size: 12px;
  margin-top: 2px;
}
.field-hint.error {
  color: var(--danger-color);
}

.password-rules {
  background: #f8fafc;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px 16px;
}

.rule-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.password-rules ul {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.password-rules li {
  font-size: 12px;
  color: var(--text-secondary);
  padding-left: 16px;
  position: relative;
}

.password-rules li::before {
  content: '○';
  position: absolute;
  left: 0;
  color: var(--text-secondary);
}

.password-rules li.valid {
  color: var(--success-color);
}

.password-rules li.valid::before {
  content: '✓';
  color: var(--success-color);
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff0f0;
  border: 1px solid #ffcdd2;
  border-radius: 8px;
  color: var(--danger-color);
  font-size: 13px;
}

.btn-submit {
  width: 100%;
  padding: 13px;
  font-size: 15px;
  font-weight: 600;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
}

.btn-submit:hover:not(:disabled) {
  background: var(--primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(26, 115, 232, 0.3);
}

.btn-submit:disabled {
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

/* ====== 成功状态 ====== */
.success-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  animation: fadeIn 0.4s ease;
}

.success-icon {
  margin-bottom: 16px;
}

.success-title {
  font-size: 22px;
  font-weight: 700;
  color: #065f46;
  margin-bottom: 8px;
}

.success-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  text-align: center;
  margin-bottom: 4px;
}

.redirect-hint {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 16px;
}

/* ====== 动画 ====== */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

/* ====== 移动端暗色适配 ====== */
html.dark .cp-container,
html.dark .cp-container.mode-standalone {
  background: #1e293b;
  border-color: #334155;
}

html.dark .input-wrapper input {
  background: #0f172a;
  border-color: #334155;
  color: #e2e8f0;
}

html.dark .input-wrapper input:focus {
  background: #1e293b;
}

html.dark .password-rules {
  background: #0f172a;
  border-color: #334155;
}

html.dark .btn-submit:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

html.dark .error-message {
  background: #3b1a1a;
  border-color: #7f1d1d;
}

html.dark .success-title {
  color: #6ee7b7;
}
</style>
