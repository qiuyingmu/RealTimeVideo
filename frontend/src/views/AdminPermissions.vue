<template>
  <div class="permissions-page">
    <div class="page-header">
      <button v-if="isMobileRoute" class="btn-back" @click="goBack" aria-label="返回">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <div>
        <h1>设备权限管理</h1>
        <p class="page-desc">管理每个用户可以查看的设备</p>
      </div>
    </div>

    <div class="permissions-layout">
      <!-- 用户列表 -->
      <div class="user-panel">
        <div class="panel-header">
          <h2>选择用户</h2>
          <div class="search-box">
            <input v-model="userSearch" placeholder="搜索用户名..." />
          </div>
        </div>
        <div class="user-list">
          <div
            v-for="u in filteredUsers"
            :key="u.id"
            class="user-card"
            :class="{ selected: selectedUser?.id === u.id }"
            @click="selectUser(u)"
          >
            <div class="user-avatar">{{ u.displayName?.[0] || u.username[0] }}</div>
            <div class="user-info">
              <strong :title="u.displayName || u.username">{{ u.displayName || u.username }}</strong>
              <span class="user-role" :class="u.role === 'ROLE_ADMIN' ? 'admin' : 'user'">
                {{ u.role === 'ROLE_ADMIN' ? '管理员' : '普通用户' }}
              </span>
            </div>
          </div>
          <div v-if="filteredUsers.length === 0" class="empty-hint">无匹配用户</div>
        </div>
      </div>

      <!-- 权限配置 -->
      <div class="permission-panel">
        <div class="panel-header">
          <h2 v-if="selectedUser">
            {{ selectedUser.displayName || selectedUser.username }} 的设备权限
          </h2>
          <h2 v-else>请选择用户</h2>
          <div class="panel-actions">
            <button
              v-if="selectedUser"
              class="btn-select-all"
              @click="toggleSelectAll"
            >
              {{ allSelected ? '取消全选' : '全选' }}
            </button>
            <button
              v-if="selectedUser"
              class="btn-save"
              :disabled="!hasChanges"
              @click="savePermissions"
            >
              保存权限
            </button>
          </div>
        </div>

        <div v-if="selectedUser" class="device-tree">
          <div
            v-for="device in deviceGroups"
            :key="device.deviceSerial"
            class="device-group"
          >
            <label class="device-checkbox">
              <input
                type="checkbox"
                :checked="device.selected"
                @change="toggleDevice(device)"
              />
              <span class="checkbox-label">
                <strong>{{ device.deviceName || device.deviceSerial }}</strong>
                <span class="device-serial">{{ device.deviceSerial }}</span>
                <span class="channel-count">{{ device.channelCount }} 通道</span>
              </span>
            </label>
          </div>
          <div v-if="loadingDevices" class="loading-hint">加载设备中...</div>
          <div v-if="deviceGroups.length === 0 && !loadingDevices" class="empty-hint">
            暂无可授权的设备，请先同步萤石云设备
          </div>
        </div>

        <div v-else class="no-selection">
          <svg viewBox="0 0 80 80" width="60" height="60">
            <rect x="15" y="15" width="50" height="50" rx="8" fill="none" stroke="#cbd5e1" stroke-width="2"/>
            <line x1="28" y1="28" x2="52" y2="52" stroke="#cbd5e1" stroke-width="2"/>
            <line x2="28" y1="52" x1="52" y2="28" stroke="#cbd5e1" stroke-width="2"/>
          </svg>
          <p>请从左侧选择一个用户</p>
        </div>

        <!-- 保存提示 -->
        <Transition name="toast">
          <div v-if="toastMsg" class="toast" :class="toastType">
            {{ toastMsg }}
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { adminApi, ezvizApi } from '@/api'

const router = useRouter()
const route = useRoute()

const isMobileRoute = computed(() => route.path.startsWith('/mobile/'))

function goBack() {
  router.push('/mobile/settings')
}

const users = ref([])
const selectedUser = ref(null)
const userSearch = ref('')
const allChannels = ref([])
const userPermSerials = ref(new Set())
const loadingDevices = ref(true)
const toastMsg = ref('')
const toastType = ref('success')

const filteredUsers = computed(() => {
  if (!userSearch.value.trim()) return users.value
  const q = userSearch.value.toLowerCase()
  return users.value.filter(u =>
    u.username.toLowerCase().includes(q) ||
    (u.displayName || '').toLowerCase().includes(q)
  )
})

const deviceGroups = computed(() => {
  const map = {}
  for (const ch of allChannels.value) {
    const serial = ch.deviceSerial
    if (!map[serial]) {
      map[serial] = {
        deviceSerial: serial,
        deviceName: ch.deviceName || serial,
        channelCount: 0,
        selected: userPermSerials.value.has(serial)
      }
    }
    map[serial].channelCount++
  }
  return Object.values(map)
})

const allSelected = computed(() => {
  return deviceGroups.value.length > 0 && deviceGroups.value.every(d => d.selected)
})

const hasChanges = computed(() => {
  if (!selectedUser.value) return false
  const original = new Set(originalPermSerials.value)
  const current = new Set(userPermSerials.value)
  if (original.size !== current.size) return true
  for (const s of original) if (!current.has(s)) return true
  return false
})

const originalPermSerials = ref(new Set())

async function loadUsers() {
  try {
    const res = await adminApi.getUsers()
    users.value = res.data.data || []
  } catch (e) {
    console.error('加载用户失败', e)
  }
}

async function loadChannels() {
  loadingDevices.value = true
  try {
    const res = await ezvizApi.getAllChannels()
    allChannels.value = res.data.data || []
  } catch (e) {
    console.error('加载通道失败', e)
  } finally {
    loadingDevices.value = false
  }
}

async function selectUser(user) {
  selectedUser.value = user
  try {
    const res = await adminApi.getUserPermissions(user.id)
    const perms = res.data.data || []
    userPermSerials.value = new Set(perms.map(p => p.deviceSerial))
    originalPermSerials.value = new Set(userPermSerials.value)
  } catch (e) {
    console.error('加载权限失败', e)
    userPermSerials.value = new Set()
    originalPermSerials.value = new Set()
  }
}

function toggleDevice(device) {
  const newSet = new Set(userPermSerials.value)
  if (device.selected) {
    newSet.delete(device.deviceSerial)
  } else {
    newSet.add(device.deviceSerial)
  }
  userPermSerials.value = newSet
}

function toggleSelectAll() {
  if (allSelected.value) {
    userPermSerials.value = new Set()
  } else {
    userPermSerials.value = new Set(deviceGroups.value.map(d => d.deviceSerial))
  }
}

async function savePermissions() {
  if (!selectedUser.value) return
  try {
    await adminApi.setUserPermissions(selectedUser.value.id, {
      deviceSerials: Array.from(userPermSerials.value)
    })
    originalPermSerials.value = new Set(userPermSerials.value)
    showToast('权限更新成功', 'success')
  } catch (e) {
    showToast('保存失败: ' + (e.friendlyMessage || '请重试'), 'error')
  }
}

function showToast(msg, type) {
  toastMsg.value = msg
  toastType.value = type
  setTimeout(() => { toastMsg.value = '' }, 3000)
}

onMounted(() => {
  loadUsers()
  loadChannels()
})
</script>

<style scoped>
.permissions-page {
  padding: 24px;
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.page-header h1 { font-size: 22px; font-weight: 700; margin: 0; }
.page-desc { color: var(--text-secondary); font-size: 13px; margin: 4px 0 0; }

.btn-back {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 50%;
  color: var(--text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  margin-top: 2px;
}

.btn-back:active {
  background: #f1f5f9;
  color: var(--text);
}

html.dark .btn-back:active {
  background: rgba(255,255,255,0.1);
}

.permissions-layout {
  flex: 1;
  display: flex;
  gap: 20px;
  margin-top: 20px;
  min-height: 0;
}

/* 用户面板 */
.user-panel {
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.panel-header h2 { font-size: 14px; font-weight: 700; margin: 0; }

.search-box input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}

.search-box input:focus { border-color: var(--primary); }

.user-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.user-card:hover { background: #f1f5f9; }
.user-card.selected { background: #eff6ff; border: 1px solid var(--primary); }

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-info strong {
  display: block;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}

.user-role.admin { background: #fef3c7; color: #92400e; }
.user-role.user { background: #e0f2fe; color: #075985; }

/* 权限面板 */
.permission-panel {
  flex: 1;
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.btn-select-all, .btn-save {
  padding: 6px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-select-all {
  background: #f1f5f9;
  color: var(--text-secondary);
}

.btn-select-all:hover { background: #e2e8f0; }

.btn-save {
  background: var(--primary);
  color: #fff;
}

.btn-save:hover { opacity: 0.9; }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

.device-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}

.device-group {
  padding: 4px 0;
}

.device-checkbox {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.device-checkbox:hover { background: #f8fafc; }

.device-checkbox input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: var(--primary);
}

.checkbox-label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.checkbox-label strong { font-size: 14px; }

.device-serial {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: monospace;
}

.channel-count {
  font-size: 11px;
  color: var(--text-secondary);
  margin-left: auto;
}

.no-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary);
}

.loading-hint, .empty-hint {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

/* Toast */
.toast {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 24px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  z-index: 100;
}

.toast.success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
.toast.error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }

.toast-enter-active, .toast-leave-active { transition: all 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }

/* 移动端 */
@media (max-width: 768px) {
  .permissions-page { padding: 12px; height: calc(100vh - 56px); }
  .permissions-layout { flex-direction: column; gap: 12px; }
  .user-panel { width: 100%; min-width: 0; max-height: 180px; }

  .user-card { padding: 12px; min-height: 48px; }

  .device-checkbox { padding: 14px 12px; min-height: 48px; }
  .device-checkbox input[type="checkbox"] { width: 22px; height: 22px; }

  .panel-actions { gap: 12px; }
  .btn-select-all, .btn-save { padding: 10px 20px; font-size: 14px; min-height: 44px; }
}
</style>
