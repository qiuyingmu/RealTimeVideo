<template>
  <div class="mobile-dashboard">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <svg viewBox="0 0 24 24" width="16" height="16" class="search-icon">
        <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/>
        <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2"/>
      </svg>
      <input v-model="searchQuery" placeholder="搜索设备或通道..." class="search-input" />
      <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''">
        <svg viewBox="0 0 24 24" width="16" height="16">
          <line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && channels.length === 0" class="loading-state">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 设备分组列表 -->
    <div v-else class="device-scroll">
      <template v-if="filteredGroups.length > 0">
        <div
          v-for="group in filteredGroups"
          :key="group.deviceSerial"
          class="device-group"
        >
          <!-- 设备头部 -->
          <div class="device-group-header" @click="expandedHande(group)">
            <svg
              class="group-arrow"
              :class="{ expanded: isExpanded(group) }"
              viewBox="0 0 24 24"
              width="18" height="18"
            >
              <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <div class="group-device-info">
              <span class="group-device-name">{{ group.deviceName }}</span>
              <span class="group-device-serial">{{ group.deviceSerial }}</span>
            </div>
            <span class="group-badge" :class="group.hasOnline ? 'online' : 'offline'">
              {{ group.channels.length }}通道
            </span>
            <span class="group-status-text" :class="group.hasOnline ? 'online' : 'offline'">
              {{ group.hasOnline ? '在线' : '离线' }}
            </span>
          </div>

          <!-- 通道列表 -->
          <div v-show="isExpanded(group)" class="group-channels">
            <div
              v-for="ch in group.channels"
              :key="ch.deviceSerial + '-' + ch.channelNo"
              class="channel-item"
              :class="{ 'channel-offline': ch.status !== 'online' }"
              @click="selectAndPlay(ch)"
            >
              <span class="ch-status-dot" :class="ch.status"></span>
              <div class="ch-info">
                <span class="ch-name">{{ ch.channelName || '通道' + ch.channelNo }}</span>
                <span class="ch-meta">CH{{ ch.channelNo }}</span>
              </div>
              <span class="ch-status-label" :class="ch.status">
                {{ ch.status === 'online' ? '在线' : '离线' }}
              </span>
              <svg viewBox="0 0 24 24" width="16" height="16" class="ch-chevron">
                <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
              </svg>
            </div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <svg viewBox="0 0 80 80" width="60" height="60">
          <rect x="20" y="20" width="40" height="30" rx="4" fill="none" stroke="#cbd5e1" stroke-width="2"/>
          <circle cx="40" cy="32" r="7" fill="none" stroke="#cbd5e1" stroke-width="2"/>
          <polygon points="37,29 37,35 44,32" fill="#cbd5e1"/>
        </svg>
        <p v-if="searchQuery">未找到匹配的设备或通道</p>
        <p v-else>暂无设备</p>
        <p class="hint" v-if="!searchQuery">请同步萤石云设备或联系管理员</p>
      </div>
    </div>

    <!-- 底部操作区 -->
    <div v-if="adminActions.length > 0" class="bottom-actions">
      <button v-for="action in adminActions" :key="action.label" class="action-btn" @click="action.handler">
        {{ action.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted,reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ezvizApi } from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const channels = ref([])
const loading = ref(true)
const searchQuery = ref('')
let refreshInterval = null
const expandedMap = reactive({})

// 按设备分组（同 Dashboard.vue 逻辑）
const deviceGroups = computed(() => {
  const map = {}
  for (const ch of channels.value) {
    const serial = ch.deviceSerial
    if (!map[serial]) {
      map[serial] = {
        deviceName: ch.deviceName || serial,
        deviceSerial: serial,
        expanded: true,
        hasOnline: false,
        channels: []
      }
    }
    map[serial].channels.push(ch)
    if (ch.status === 'online') map[serial].hasOnline = true
  }
  // 每个设备组内：在线通道排前面
  for (const g of Object.values(map)) {
    g.channels.sort((a, b) => {
      if (a.status === 'online' && b.status !== 'online') return -1
      if (a.status !== 'online' && b.status === 'online') return 1
      return (a.channelName || '').localeCompare(b.channelName || '')
    })
  }
  // 设备组之间：有在线通道的组排前面
  return Object.values(map).sort((a, b) => {
    if (a.hasOnline && !b.hasOnline) return -1
    if (!a.hasOnline && b.hasOnline) return 1
    return (a.deviceName || '').localeCompare(b.deviceName || '')
  })
})

// 搜索过滤（同时搜索设备和通道名）
const filteredGroups = computed(() => {
  if (!searchQuery.value.trim()) return deviceGroups.value
  const q = searchQuery.value.toLowerCase()
  return deviceGroups.value.map(g => ({
    ...g,
    expanded: isExpanded(g),
    channels: g.channels.filter(ch =>
      (ch.channelName || '').toLowerCase().includes(q) ||
      String(ch.channelNo).includes(q) ||
      ch.deviceSerial.toLowerCase().includes(q) ||
      (ch.deviceName || '').toLowerCase().includes(q)
    )
  })).filter(g => g.channels.length > 0)
})

// 管理员快捷操作
const adminActions = computed(() => {
  if (!authStore.isAdmin) return []
  return [{
    label: '刷新设备列表',
    handler: refreshChannels
  }]
})

function selectAndPlay(ch) {
  // 保存选中的通道到 sessionStorage，然后跳转到播放页
  sessionStorage.setItem('mobileSelectedChannel', JSON.stringify(ch))
  router.push('/mobile/play')
}

async function refreshChannels() {
  loading.value = true
  try {
    const res = await ezvizApi.getAllChannels()
    channels.value = res.data.data || []
  } catch (e) {
    console.error('刷新通道失败', e)
  } finally {
    loading.value = false
  }
}

function expandedHande(group) {
  const key = group.deviceSerial
  expandedMap[key] = !(expandedMap[key] ?? true)
}

function isExpanded(group) {
  return expandedMap[group.deviceSerial] ?? true
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await ezvizApi.getAllChannels()
    channels.value = res.data.data || []
  } catch (e) {
    console.error('加载通道失败', e)
  } finally {
    loading.value = false
  }

  // 每30秒刷新状态（智能合并，保留对象引用避免卡片闪烁）
  refreshInterval = setInterval(async () => {
    try {
      const res = await ezvizApi.getAllChannels()
      const newChannels = res.data.data || []
      const oldChannels = channels.value

      if (oldChannels.length === 0) {
        // 首次无数据，直接赋值
        channels.value = newChannels
        return
      }

      // 建立旧通道的快速查找索引
      const oldMap = new Map()
      oldChannels.forEach((ch, i) => {
        oldMap.set(ch.deviceSerial + '-' + ch.channelNo, { ch, index: i })
      })

      // 遍历新数据，尝试复用旧对象
      const merged = []
      const seen = new Set()

      for (const newCh of newChannels) {
        const key = newCh.deviceSerial + '-' + newCh.channelNo
        seen.add(key)
        const existing = oldMap.get(key)
        if (existing) {
          // 复用旧对象，只更新动态字段（保持引用稳定，Vue 不重建 DOM）
          existing.ch.status = newCh.status
          existing.ch.deviceName = newCh.deviceName
          existing.ch.channelName = newCh.channelName
          merged.push(existing.ch)
        } else {
          // 新出现的通道，push 新对象
          merged.push(newCh)
        }
      }

      // 检查是否有被删除的通道，有则全量替换（避免残留）
      if (seen.size !== oldMap.size) {
        channels.value = merged
      } else {
        // 数量相同且所有旧通道都在新列表中 — 保持引用不变
        // 需要在响应式数组层面触发更新，但保留对象引用
        // 用 splice 替换数组内容而不改变数组引用
        channels.value.splice(0, channels.value.length, ...merged)
      }
    } catch (e) {}
  }, 30000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
})
</script>

<style scoped>
.mobile-dashboard {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 12px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.search-icon {
  color: #94a3b8;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: #1a1a2e;
  background: transparent;
}

.search-input::placeholder {
  color: #94a3b8;
}

.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: transparent;
  border: none;
  border-radius: 50%;
  color: #94a3b8;
  cursor: pointer;
  flex-shrink: 0;
}

.search-clear:active {
  background: #f1f5f9;
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 12px;
  color: #64748b;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e2e8f0;
  border-top-color: #1a73e8;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 设备滚动列表 */
.device-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
  -webkit-overflow-scrolling: touch;
}

/* ====== 设备分组 ====== */
.device-group {
  margin-bottom: 10px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

/* 设备头部 */
.device-group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  transition: background 0.15s;
}

.device-group-header:active {
  background: #f8fafc;
}

.group-arrow {
  color: #94a3b8;
  flex-shrink: 0;
  transition: transform 0.2s;
}

.group-arrow.expanded {
  transform: rotate(90deg);
}

.group-device-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.group-device-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-device-serial {
  font-size: 11px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}

.group-badge.online {
  background: #e8f5e9;
  color: #2e7d32;
}

.group-badge.offline {
  background: #f1f5f9;
  color: #94a3b8;
}

.group-status-text {
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.group-status-text.online {
  color: #2e7d32;
}

.group-status-text.offline {
  color: #94a3b8;
}

/* ====== 通道列表（设备下方） ====== */
.group-channels {
  border-top: 1px solid #f1f5f9;
}

.channel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px 12px 18px;
  cursor: pointer;
  transition: background 0.15s;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  border-bottom: 1px solid #f8fafc;
}

.channel-item:last-child {
  border-bottom: none;
}

.channel-item:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.channel-item.channel-offline {
  opacity: 0.65;
}

.ch-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ch-status-dot.online { background: #2e7d32; }
.ch-status-dot.offline { background: #cbd5e1; }

.ch-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ch-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ch-meta {
  font-size: 11px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ch-status-label {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  flex-shrink: 0;
}

.ch-status-label.online {
  background: #dcfce7;
  color: #166534;
}

.ch-status-label.offline {
  background: #f1f5f9;
  color: #94a3b8;
}

.ch-chevron {
  color: #cbd5e1;
  flex-shrink: 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 8px;
  color: #64748b;
}

.empty-state svg {
  opacity: 0.4;
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
}

.empty-state .hint {
  font-size: 12px;
  color: #1a73e8;
}

/* 底部操作 */
.bottom-actions {
  padding: 8px 12px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0));
  flex-shrink: 0;
}

.action-btn {
  width: 100%;
  padding: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1a73e8;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}

.action-btn:active {
  background: #f1f5f9;
}
</style>
