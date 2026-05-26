<template>
  <div class="mobile-dashboard">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <svg viewBox="0 0 24 24" width="16" height="16" class="search-icon">
        <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/>
        <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2"/>
      </svg>
      <input v-model="searchQuery" placeholder="搜索通道名称..." class="search-input" />
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && channels.length === 0" class="loading-state">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 设备卡片列表 -->
    <div v-else class="device-scroll">
      <template v-if="allChannelList.length > 0">
        <div
          v-for="ch in allChannelList"
          :key="ch.deviceSerial + '-' + ch.channelNo"
          class="channel-card"
          :class="{ offline: ch.status !== 'online' }"
          @click="selectAndPlay(ch)"
        >
          <div class="card-preview">
            <div class="preview-placeholder">
              <svg viewBox="0 0 24 24" width="32" height="32">
                <rect x="2" y="4" width="20" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/>
                <polygon points="10,8 16,11 10,14" fill="currentColor"/>
              </svg>
            </div>
            <span class="status-dot" :class="ch.status"></span>
          </div>
          <div class="card-info">
            <div class="card-name">
              {{ ch.channelName || '通道' + ch.channelNo }}
            </div>
            <div class="card-meta">
              {{ ch.deviceName || ch.deviceSerial }} · CH{{ ch.channelNo }}
            </div>
          </div>
          <div class="card-status">
            <span class="status-text" :class="ch.status">
              {{ ch.status === 'online' ? '在线' : '离线' }}
            </span>
            <svg viewBox="0 0 24 24" width="16" height="16" class="chevron">
              <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
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
        <p>暂无设备</p>
        <p class="hint">请同步萤石云设备或联系管理员</p>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ezvizApi } from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const channels = ref([])
const loading = ref(true)
const searchQuery = ref('')
let refreshInterval = null

// 所有通道扁平列表
const allChannelList = computed(() => {
  if (!searchQuery.value.trim()) return channels.value
  const q = searchQuery.value.toLowerCase()
  return channels.value.filter(ch =>
    (ch.channelName || '').toLowerCase().includes(q) ||
    String(ch.channelNo).includes(q) ||
    ch.deviceSerial.toLowerCase().includes(q)
  )
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

/* 通道卡片 */
.channel-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}

.channel-card:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.channel-card.offline {
  opacity: 0.65;
}

/* 预览缩略图 */
.card-preview {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #1e293b;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}

.preview-placeholder {
  color: rgba(255,255,255,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid #1e293b;
}

.status-dot.online {
  background: #22c55e;
}

.status-dot.offline {
  background: #94a3b8;
}

/* 卡片信息 */
.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.card-meta {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片右侧状态 */
.card-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.status-text {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
}

.status-text.online {
  background: #dcfce7;
  color: #166534;
}

.status-text.offline {
  background: #f1f5f9;
  color: #94a3b8;
}

.chevron {
  color: #cbd5e1;
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
