<template>
  <div class="dashboard-layout">
    <!-- 移动端遮罩层 -->
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"></div>

    <!-- 左侧：设备/通道树 -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-header">
        <h2>设备列表</h2>
        <div class="sidebar-actions">
          <button
            class="btn-header-action"
            @click="refreshChannels"
            :disabled="loading"
            title="刷新通道列表"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" :class="{ spinning: loading }">
              <path d="M21.5 2v6h-6M2.5 22v-6h6M2.5 16.5A10 10 0 0112 2c4.16 0 7.74 2.57 9.17 6.17M21.5 7.5A10 10 0 0112 22c-4.16 0-7.74-2.57-9.17-6.17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
          <button v-if="authStore.isAdmin" class="btn-header-action" :disabled="syncing" @click="syncFromEzviz" title="从萤石云同步">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path d="M5 12h14M12 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 搜索/过滤 -->
      <div class="search-box">
        <svg viewBox="0 0 24 24" width="16" height="16">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/>
          <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2"/>
        </svg>
        <input v-model="searchQuery" placeholder="搜索通道名称..." />
      </div>

      <!-- 快捷操作 -->
      <div v-if="authStore.isAdmin" class="quick-actions">
        <button class="quick-btn" @click="enablePtzAll" title="开启所有通道的云台控制功能">
          <svg viewBox="0 0 24 24" width="14" height="14"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/><polyline points="12 6 12 12 16 14" fill="none" stroke="currentColor" stroke-width="2"/></svg>
          开启全部PTZ
        </button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && channels.length === 0" class="sidebar-loading">
        <div class="mini-spinner"></div>
      </div>

      <!-- 设备树 -->
      <div v-else-if="filteredGroups.length > 0" class="device-tree">
        <div
          v-for="group in filteredGroups"
          :key="group.deviceSerial"
          class="tree-group"
        >
          <div class="tree-group-header" @click="group.expanded = !group.expanded">
            <svg
              class="arrow-icon"
              :class="{ expanded: group.expanded }"
              viewBox="0 0 24 24"
              width="16" height="16"
            >
              <polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
            <span class="group-name" :title="group.deviceName">{{ group.deviceName }}</span>
            <span class="group-badge" :class="group.hasOnline ? 'online' : 'offline'">
              {{ group.channels.length }}
            </span>
          </div>

          <div v-show="group.expanded" class="tree-channels">
            <div
              v-for="ch in group.channels"
              :key="ch.id"
              class="tree-channel"
              :class="{ active: selectedChannel?.id === ch.id, 'offline-item': ch.status !== 'online' }"
              @click="selectChannel(ch)"
            >
              <span class="ch-status-dot" :class="ch.status"></span>
              <span class="ch-name" :title="ch.channelName || '通道' + ch.channelNo">{{ ch.channelName || '通道' + ch.channelNo }}</span>
              <span class="ch-no">CH{{ ch.channelNo }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="sidebar-empty">
        <p>暂无设备</p>
        <p v-if="authStore.isAdmin" class="hint">点击同步按钮从萤石云获取设备</p>
        <p v-else class="hint">请联系管理员添加设备</p>
      </div>
    </aside>

    <!-- 右侧：主视频区域 -->
    <main class="main-content">
      <!-- 有选中的通道 -->
      <template v-if="selectedChannel">
        <div class="video-section">
          <div class="video-header">
            <div class="video-title-area">
              <!-- 移动端：设备列表切换按钮 -->
              <button class="btn-sidebar-toggle" @click="sidebarOpen = !sidebarOpen" aria-label="切换设备列表">
                <svg viewBox="0 0 24 24" width="20" height="20">
                  <line x1="3" y1="6" x2="21" y2="6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  <line x1="3" y1="18" x2="21" y2="18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </button>
              <div class="video-title">
                <strong>{{ selectedChannel.channelName || '通道' + selectedChannel.channelNo }}</strong>
                <span class="video-meta" :title="getDeviceName(selectedChannel.deviceSerial) + ' · CH' + selectedChannel.channelNo">
                  {{ getDeviceName(selectedChannel.deviceSerial) }} · CH{{ selectedChannel.channelNo }}
                </span>
              </div>
            </div>
            <span class="video-status" :class="selectedChannel.status">
              <span class="status-dot"></span>
              {{ selectedChannel.status === 'online' ? '在线' : '离线' }}
            </span>
          </div>
          <div class="video-container" ref="videoContainer">
            <VideoPlayer
              v-if="playerKey"
              :channel="selectedChannel"
              :cached-token="ezvizToken?.accessToken || null"
              :key="playerKey"
              @prev-channel="goToPrevChannel"
              @next-channel="goToNextChannel"
            />
          </div>
        </div>
      </template>

      <!-- 未选择通道 -->
      <template v-else>
        <div class="welcome-screen">
          <svg viewBox="0 0 120 120" width="100" height="100">
            <rect x="20" y="20" width="80" height="60" rx="8" fill="none" stroke="#cbd5e1" stroke-width="2"/>
            <circle cx="60" cy="45" r="14" fill="none" stroke="#cbd5e1" stroke-width="2"/>
            <polygon points="54,39 54,51 66,45" fill="#cbd5e1"/>
            <line x1="35" y1="80" x2="45" y2="70" stroke="#cbd5e1" stroke-width="2"/>
            <line x1="85" y1="80" x2="75" y2="70" stroke="#cbd5e1" stroke-width="2"/>
            <line x1="38" y1="90" x2="82" y2="90" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <h2>请从左侧选择一个通道</h2>
          <p>点击设备左侧箭头展开，点击通道开始播放实时视频</p>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, defineAsyncComponent } from 'vue'
import { useAuthStore } from '@/store/auth'
import { ezvizApi } from '@/api'

const VideoPlayer = defineAsyncComponent(() => import('@/components/VideoPlayer.vue'))

const authStore = useAuthStore()

const channels = ref([])
const loading = ref(true)
const syncing = ref(false)
const selectedChannel = ref(null)
const playerKey = ref(0)
const searchQuery = ref('')
const videoContainer = ref(null)
const sidebarOpen = ref(false)

// ---- EZVIZ Token 缓存（全局共享，跨组件） ----
const ezvizToken = ref(null)
const tokenLoading = ref(false)

// ---- 定时刷新 ----
let refreshInterval = null

/** 获取萤石云 accessToken（带缓存） */
async function fetchEzvizToken(forceRefresh = false) {
  if (!forceRefresh && ezvizToken.value && Date.now() < ezvizToken.value.expiresAt) {
    return { accessToken: ezvizToken.value.accessToken, appKey: ezvizToken.value.appKey }
  }
  tokenLoading.value = true
  try {
    const res = await ezvizApi.getToken()
    const { accessToken, appKey, expiresIn } = res.data.data
    // 缓存到过期前5分钟（安全余量）
    const ttl = (parseInt(expiresIn || '3600') - 300) * 1000
    ezvizToken.value = {
      accessToken,
      appKey,
      expiresAt: Date.now() + Math.max(ttl, 60000) // 至少缓存1分钟
    }
    return { accessToken, appKey }
  } catch (err) {
    console.error('获取EZVIZ Token失败', err)
    return { accessToken: null, appKey: null }
  } finally {
    tokenLoading.value = false
  }
}

// 按设备分组（在线通道优先排序）
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
      return (a.channelName || '') > (b.channelName || '') ? 1 : -1
    })
  }
  // 设备组之间：有在线通道的组排前面
  return Object.values(map).sort((a, b) => {
    if (a.hasOnline && !b.hasOnline) return -1
    if (!a.hasOnline && b.hasOnline) return 1
    return (a.deviceName || '').localeCompare(b.deviceName || '')
  })
})

// 搜索过滤
const filteredGroups = computed(() => {
  if (!searchQuery.value.trim()) return deviceGroups.value
  const q = searchQuery.value.toLowerCase()
  return deviceGroups.value.map(g => ({
    ...g,
    expanded: true,
    channels: g.channels.filter(ch =>
      (ch.channelName || '').toLowerCase().includes(q) ||
      String(ch.channelNo).includes(q) ||
      ch.deviceSerial.toLowerCase().includes(q)
    ).sort((a, b) => {
      if (a.status === 'online' && b.status !== 'online') return -1
      if (a.status !== 'online' && b.status === 'online') return 1
      return (a.channelName || '').localeCompare(b.channelName || '')
    })
  })).filter(g => g.channels.length > 0)
    .sort((a, b) => {
      if (a.hasOnline && !b.hasOnline) return -1
      if (!a.hasOnline && b.hasOnline) return 1
      return (a.deviceName || '').localeCompare(b.deviceName || '')
    })
})

// 自动选择第一个在线通道
function selectFirstOnline() {
  for (const g of deviceGroups.value) {
    for (const ch of g.channels) {
      if (ch.status === 'online') {
        selectChannel(ch)
        return
      }
    }
  }
  if (deviceGroups.value.length > 0 && deviceGroups.value[0].channels.length > 0) {
    selectChannel(deviceGroups.value[0].channels[0])
  }
}

function selectChannel(ch) {
  selectedChannel.value = ch
  playerKey.value++
  // 移动端：选择通道后关闭侧边栏
  sidebarOpen.value = false
  fetchEzvizToken()
}

function getDeviceName(serial) {
  const g = deviceGroups.value.find(g => g.deviceSerial === serial)
  return g?.deviceName || serial
}

/** 获取当前选中的通道在列表中的索引 */
function getCurrentChannelIndex() {
  const allChannels = channels.value
  if (!selectedChannel.value || allChannels.length === 0) return -1
  return allChannels.findIndex(c =>
    c.deviceSerial === selectedChannel.value.deviceSerial &&
    c.channelNo === selectedChannel.value.channelNo
  )
}

/** 切换到上一个通道 */
function goToPrevChannel() {
  const allChannels = channels.value
  if (allChannels.length === 0) return
  const idx = getCurrentChannelIndex()
  if (idx <= 0) {
    selectChannel(allChannels[allChannels.length - 1]) // 循环到最后一个
  } else {
    selectChannel(allChannels[idx - 1])
  }
}

/** 切换到下一个通道 */
function goToNextChannel() {
  const allChannels = channels.value
  if (allChannels.length === 0) return
  const idx = getCurrentChannelIndex()
  if (idx < 0 || idx >= allChannels.length - 1) {
    selectChannel(allChannels[0]) // 循环到第一个
  } else {
    selectChannel(allChannels[idx + 1])
  }
}

/** 刷新通道列表（保持当前选中的通道） */
async function refreshChannels() {
  loading.value = true
  try {
    const response = await ezvizApi.getAllChannels()
    channels.value = response.data.data || []
  } catch (err) {
    console.error('刷新通道列表失败', err)
  } finally {
    loading.value = false
  }
}

/** 开启所有通道的 PTZ 云台控制 */
async function enablePtzAll() {
  if (!confirm('确定开启所有通道的云台控制(PTZ)功能吗？')) return
  try {
    const res = await ezvizApi.enableAllPtz()
    alert(res.data.message)
    await refreshChannels()
  } catch (err) {
    alert('操作失败: ' + (err.friendlyMessage || '请重试'))
  }
}

onMounted(async () => {
  loading.value = true
  // 并行加载：通道列表 + 预取Token
  const [response] = await Promise.all([
    ezvizApi.getAllChannels(),
    fetchEzvizToken().catch(() => {})
  ])
  channels.value = response.data.data || []
  await nextTick()
  selectFirstOnline()
  loading.value = false

  // 定时刷新通道状态（每30秒自动刷新一次，保持设备在线状态最新）
  // 注意：不替换选中通道的对象引用，仅更新status字段，避免VideoPlayer因prop变化而抖动
  refreshInterval = setInterval(() => {
    ezvizApi.getAllChannels().then(res => {
      const newChannels = res.data.data || []
      // 只更新选中通道的状态字段（保留引用，防止VideoPlayer重建）
      if (selectedChannel.value) {
        const updated = newChannels.find(c =>
          c.deviceSerial === selectedChannel.value.deviceSerial &&
          c.channelNo === selectedChannel.value.channelNo
        )
        if (updated) {
          selectedChannel.value.status = updated.status
          // 更新其他动态字段但不替换对象引用
          selectedChannel.value.deviceName = updated.deviceName
          selectedChannel.value.channelName = updated.channelName
        }
      }
      channels.value = newChannels
    }).catch(() => {})
  }, 30000)

  // 监听全屏/横屏事件，用于自动隐藏导航栏
  document.addEventListener('video-fullscreen-change', onFullscreenChange)
  document.addEventListener('video-orientation-change', onOrientationChange)
})

function onFullscreenChange(e) {
  document.body.classList.toggle('video-fullscreen', e.detail.isFullscreen)
}

function onOrientationChange(e) {
  document.body.classList.toggle('video-landscape', e.detail.landscape)
}

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
  document.removeEventListener('video-fullscreen-change', onFullscreenChange)
  document.removeEventListener('video-orientation-change', onOrientationChange)
  document.body.classList.remove('video-fullscreen', 'video-landscape')
})

async function syncFromEzviz() {
  syncing.value = true
  try {
    const response = await ezvizApi.syncDevices()
    await refreshChannels()
    alert(response.data.message)
  } catch (err) {
    alert('同步失败: ' + (err.friendlyMessage || '请重试'))
  } finally {
    syncing.value = false
  }
}
</script>

<style scoped>
.dashboard-layout {
  display: flex;
  height: calc(100vh - 64px);
  gap: 0;
  margin: -24px;
}

/* ====== 左侧边栏 ====== */
.sidebar {
  width: 320px;
  min-width: 320px;
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.sidebar-header h2 {
  font-size: 16px;
  font-weight: 700;
}

.sidebar-actions {
  display: flex;
  gap: 4px;
}

.btn-header-action {
  padding: 6px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-header-action:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.btn-header-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin: 12px 16px;
  background: #f1f5f9;
  border-radius: 8px;
  color: var(--text-secondary);
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: var(--text);
}

.search-box input::placeholder {
  color: var(--text-secondary);
}

.quick-actions {
  padding: 4px 16px 8px;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  background: #f1f5f9;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-btn:hover {
  background: #e8f5e9;
  color: #2e7d32;
  border-color: #a5d6a7;
}

.sidebar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.mini-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.sidebar-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-secondary);
}

.sidebar-empty .hint {
  font-size: 12px;
  margin-top: 8px;
  color: var(--primary);
}

/* 设备树 */
.device-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.tree-group-header:hover {
  background: #f8fafc;
}

.arrow-icon {
  color: var(--text-secondary);
  transition: transform 0.2s;
  flex-shrink: 0;
}

.arrow-icon.expanded {
  transform: rotate(90deg);
}

.group-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 8px;
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

.tree-channels {
  padding: 2px 0;
}

.tree-channel {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 8px 36px;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}

.tree-channel:hover {
  background: #f1f5f9;
}

.tree-channel.active {
  background: #eff6ff;
  border-left-color: var(--primary);
}

.tree-channel.offline-item {
  opacity: 0.6;
}

.ch-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ch-status-dot.online { background: #2e7d32; }
.ch-status-dot.offline { background: #cbd5e1; }

.ch-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ch-no {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 600;
  flex-shrink: 0;
}

/* ====== 右侧主内容 ====== */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  overflow: hidden;
}

.video-section {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.video-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.video-title-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-sidebar-toggle {
  display: none;
  padding: 6px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;
  align-items: center;
  justify-content: center;
}

.btn-sidebar-toggle:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.video-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.video-title strong {
  font-size: 16px;
}

.video-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.video-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.video-status.online { color: #2e7d32; }
.video-status.offline { color: #94a3b8; }

.video-container {
  flex: 1 1 0;
  padding: 0;
  background: #000;
  min-height: 200px;
  min-width: 0;
  position: relative;
  overflow: hidden;
}

/* 欢迎页 */
.welcome-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-secondary);
}

.welcome-screen svg {
  opacity: 0.4;
}

.welcome-screen h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.spinning {
  animation: spin 1s linear infinite;
}

/* ====== 移动端响应式 ====== */
.sidebar-overlay {
  display: none;
}

@media (max-width: 768px) {
  .dashboard-layout {
    flex-direction: column;
    height: calc(100vh - 56px);
    margin: -16px;
  }

  /* 侧边栏：抽屉式滑入 */
  .sidebar {
    position: fixed;
    top: 56px;
    left: 0;
    bottom: 0;
    width: 280px;
    min-width: 280px;
    z-index: 200;
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.15);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 150;
  }

  /* 移动端：显示侧边栏切换按钮 */
  .btn-sidebar-toggle {
    display: flex;
  }

  /* 视频区域 */
  .main-content {
    width: 100%;
  }

  .video-header {
    padding: 10px 12px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .video-title strong {
    font-size: 14px;
  }

  .video-title-area {
    flex: 1;
    min-width: 0;
  }

  .video-meta {
    font-size: 11px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .video-container {
    padding: 0;
    flex: 1 1 0;
    min-height: 30vh;
    max-height: none;
    position: relative;
    overflow: hidden;
  }

  /* 小屏手机：视频最大化 */
  @media (max-height: 700px) {
    .video-container {
      min-height: 40vh;
    }
  }

  /* 横屏模式：视频占满全屏 */
  @media (max-height: 500px) and (orientation: landscape) {
    .video-container {
      min-height: calc(100vh - 56px - 40px);
    }
    .video-header {
      padding: 4px 12px;
    }
    .video-title strong {
      font-size: 13px;
    }
    .video-status {
      font-size: 11px;
    }
  }

  /* 设备列表按钮增大触摸区域 */
  .tree-channel {
    padding: 12px 12px 12px 32px;
    min-height: 44px;
  }

  .tree-group-header {
    padding: 14px 12px;
    min-height: 44px;
  }

  .btn-sidebar-toggle {
    min-width: 44px;
    min-height: 44px;
    justify-content: center;
  }

  .video-status {
    font-size: 12px;
  }

  /* 欢迎页 */
  .welcome-screen {
    padding: 20px;
  }

  .welcome-screen h2 {
    font-size: 17px;
  }

  .welcome-screen p {
    font-size: 13px;
    text-align: center;
  }
}
</style>
