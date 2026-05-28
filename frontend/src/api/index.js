import axios from 'axios'
import { useAuthStore } from '@/store/auth'

// 创建 Axios 实例
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 自动添加Token
api.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器 - 处理Token过期、自动刷新
let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // 401 未授权，尝试刷新Token
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const authStore = useAuthStore()
      if (!authStore.accessToken) {
        authStore.logout()
        isRefreshing = false
        return Promise.reject(normalizeError(error))
      }

      try {
        // refreshToken 通过 httpOnly Cookie 自动携带，无需在请求体中发送
        const response = await axios.post('/api/auth/refresh')

        const { accessToken } = response.data.data
        authStore.setTokens(accessToken)

        processQueue(null, accessToken)
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return api(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError, null)
        authStore.logout()
        return Promise.reject(normalizeError(refreshError))
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(normalizeError(error))
  }
)

/**
 * 统一错误消息格式化 — 绝不暴露原始堆栈/异常信息
 */
function normalizeError(error) {
  const response = error.response
  if (!response) {
    // 网络不通 / 超时
    if (error.code === 'ECONNABORTED') {
      error.friendlyMessage = '请求超时，请检查网络后重试'
    } else if (error.code === 'ERR_NETWORK') {
      error.friendlyMessage = '网络连接失败，请检查网络'
    } else {
      error.friendlyMessage = '网络异常，请稍后重试'
    }
    return error
  }

  const status = response.status
  const serverMsg = response.data?.message || ''

  switch (status) {
    case 400:
      error.friendlyMessage = serverMsg || '请求参数有误'
      // 追加字段级错误信息
      if (response.data?.data && typeof response.data.data === 'object') {
        const fieldErrors = Object.entries(response.data.data)
          .map(([field, msg]) => `${field}: ${msg}`).join('; ')
        if (fieldErrors) {
          error.friendlyMessage += ' (' + fieldErrors + ')'
        }
      }
      break
    case 401:
      error.friendlyMessage = serverMsg || '请重新登录'
      break
    case 403:
      error.friendlyMessage = '权限不足，无法执行此操作'
      break
    case 404:
      error.friendlyMessage = serverMsg || '请求的数据不存在'
      break
    case 429:
      error.friendlyMessage = '操作过于频繁，请稍后再试'
      break
    case 500:
    case 502:
    case 503:
      error.friendlyMessage = '服务器繁忙，请稍后重试'
      break
    default:
      error.friendlyMessage = serverMsg || '请求失败，请重试'
  }
  return error
}

// ========== API 方法 ==========

// 认证
export const authApi = {
  login: (username, password) =>
    api.post('/auth/login', { username, password }),

  refresh: () =>
    api.post('/auth/refresh'),

  logout: () =>
    api.post('/auth/logout'),

  me: () =>
    api.get('/auth/me')
}

// 萤石云
export const ezvizApi = {
  getToken: () =>
    api.get('/ezviz/token'),
  syncDevices: () =>
    api.post('/ezviz/sync-devices'),
  getAllChannels: () =>
    api.get('/ezviz/channels'),
  getDeviceChannels: (deviceSerial) =>
    api.get(`/ezviz/devices/${deviceSerial}/channels`),
  enableAllPtz: () =>
    api.put('/ezviz/channels/ptz/enable-all'),

  // PTZ 云台控制
  /**
   * PTZ 方向常量
   */
  PtzDirection: {
    LEFT: 1,
    RIGHT: 2,
    UP: 3,
    DOWN: 4,
    ZOOM_IN: 5,
    ZOOM_OUT: 6,
  },

  /**
   * 开始 PTZ 云台转动
   * @param {string} deviceSerial 设备序列号
   * @param {number} channelNo 通道号
   * @param {number} direction 方向 (1左,2右,3上,4下,5变焦+,6变焦-)
   * @param {number} [speed=50] 速度 (0~100)
   */
  startPtz: (deviceSerial, channelNo, direction, speed = 50) =>
    api.post('/ezviz/ptz/start', { deviceSerial, channelNo, direction, speed }),

  /**
   * 停止 PTZ 云台转动
   * @param {string} deviceSerial 设备序列号
   * @param {number} channelNo 通道号
   */
  stopPtz: (deviceSerial, channelNo) =>
    api.post('/ezviz/ptz/stop', { deviceSerial, channelNo }),
}

// 设备
export const deviceApi = {
  getAll: () =>
    api.get('/devices'),

  getById: (id) =>
    api.get(`/devices/${id}`),

  getBySerial: (serial) =>
    api.get(`/devices/serial/${serial}`),

  create: (device) =>
    api.post('/devices', device),

  update: (id, device) =>
    api.put(`/devices/${id}`, device),

  delete: (id) =>
    api.delete(`/devices/${id}`)
}

// 管理
export const adminApi = {
  getUsers: () =>
    api.get('/admin/users'),

  getUser: (id) =>
    api.get(`/admin/users/${id}`),

  createUser: (data) =>
    api.post('/admin/users', data),

  toggleEnabled: (id) =>
    api.put(`/admin/users/${id}/toggle-enabled`),

  resetPassword: (id, newPassword) =>
    api.put(`/admin/users/${id}/reset-password`, { newPassword }),

  deleteUser: (id) =>
    api.delete(`/admin/users/${id}`),

  // 设备权限管理
  getPermissions: () =>
    api.get('/admin/permissions'),

  getUserPermissions: (userId) =>
    api.get(`/admin/permissions/user/${userId}`),

  setUserPermissions: (userId, data) =>
    api.put(`/admin/permissions/user/${userId}`, data),

  removeDevicePermission: (userId, deviceSerial) =>
    api.delete(`/admin/permissions/user/${userId}/device/${deviceSerial}`),

  // 操作日志
  getLogs: (params) =>
    api.get('/admin/logs', { params })
}

// 健康检查
export const healthApi = {
  check: () =>
    api.get('/health')
}

export default api
