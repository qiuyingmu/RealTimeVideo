import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  // State
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const username = ref(localStorage.getItem('username') || '')
  const displayName = ref(localStorage.getItem('displayName') || '')
  const userRole = ref(localStorage.getItem('userRole') || '')

  // Getters
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => userRole.value === 'ROLE_ADMIN')

  // Actions
  function setTokens(token) {
    accessToken.value = token
    localStorage.setItem('accessToken', token)
  }

  function setUserInfo(user, name, role) {
    username.value = user
    displayName.value = name
    userRole.value = role
    localStorage.setItem('username', user)
    localStorage.setItem('displayName', name)
    localStorage.setItem('userRole', role)
  }

  async function login(loginUsername, password) {
    const response = await authApi.login(loginUsername, password)
    const data = response.data.data

    // refreshToken 通过 httpOnly Cookie 自动下发，无需前端 JS 处理
    setTokens(data.accessToken)
    setUserInfo(data.username, data.displayName, data.role)

    return data
  }

  async function logout() {
    // 通知后端登出（浏览器自动携带 refreshToken cookie）
    try {
      await authApi.logout()
    } catch (e) {
      // 即使后端失败也清理本地状态
    }

    // 清理本地状态
    accessToken.value = ''
    username.value = ''
    displayName.value = ''
    userRole.value = ''

    localStorage.removeItem('accessToken')
    localStorage.removeItem('username')
    localStorage.removeItem('displayName')
    localStorage.removeItem('userRole')
  }

  return {
    accessToken,
    username,
    displayName,
    userRole,
    isAuthenticated,
    isAdmin,
    login,
    logout,
    setTokens,
    setUserInfo
  }
})
