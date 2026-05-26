import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  // State
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')
  const username = ref(localStorage.getItem('username') || '')
  const displayName = ref(localStorage.getItem('displayName') || '')
  const userRole = ref(localStorage.getItem('userRole') || '')

  // Getters
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => userRole.value === 'ROLE_ADMIN')

  // Actions
  function setTokens(token, rToken) {
    accessToken.value = token
    refreshToken.value = rToken
    localStorage.setItem('accessToken', token)
    localStorage.setItem('refreshToken', rToken)
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

    setTokens(data.accessToken, data.refreshToken)
    setUserInfo(data.username, data.displayName, data.role)

    return data
  }

  function logout() {
    // 尝试通知后端登出（不阻塞）
    if (refreshToken.value) {
      authApi.logout(refreshToken.value).catch(() => {})
    }

    accessToken.value = ''
    refreshToken.value = ''
    username.value = ''
    displayName.value = ''
    userRole.value = ''

    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    localStorage.removeItem('displayName')
    localStorage.removeItem('userRole')
  }

  return {
    accessToken,
    refreshToken,
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
