<template>
  <div class="mobile-play" ref="playPageRef">
    <!-- 视频播放器区域 -->
    <div class="player-area" ref="playerAreaRef">
      <!-- 加载中 -->
      <div v-if="!initialized && !error" class="player-loading">
        <div class="loading-spinner">
          <svg class="spinner-ring" viewBox="0 0 50 50">
            <circle cx="25" cy="25" r="20" fill="none" stroke-width="3"/>
          </svg>
        </div>
        <p class="loading-text">{{ loadMsg }}</p>
      </div>

      <!-- 播放失败 -->
      <div v-if="error" class="player-error">
        <svg viewBox="0 0 60 60" width="40" height="40">
          <circle cx="30" cy="30" r="22" fill="none" stroke="#f87171" stroke-width="2.5"/>
          <line x1="20" y1="20" x2="40" y2="40" stroke="#f87171" stroke-width="2.5"/>
          <line x1="40" y1="20" x2="20" y2="40" stroke="#f87171" stroke-width="2.5"/>
        </svg>
        <p class="error-text">{{ error }}</p>
        <button class="retry-btn" @click="initPlayer">重新连接</button>
      </div>

      <!-- 播放器容器 -->
      <div id="mobile-ezuikit-player" class="ezuikit-player" v-show="!error"></div>

      <!-- 顶部返回和信息 -->
      <div class="player-top-bar" v-if="initialized">
        <button class="top-btn" @click="goBack">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="top-info">
          <span class="top-channel-name">{{ channelName }}</span>
          <span class="top-channel-meta">{{ channelMeta }}</span>
        </div>
        <div class="top-spacer"></div>
      </div>

      <!-- 底部控制栏 -->
      <div class="player-bottom-bar" v-if="initialized" :class="{ visible: controlsVisible }">
        <button class="control-btn" @click="prevChannel" :disabled="!hasPrev">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <polygon points="19 20 9 12 19 4 19 20" fill="none" stroke="currentColor" stroke-width="2"/>
            <line x1="5" y1="4" x2="5" y2="20" stroke="currentColor" stroke-width="2"/>
          </svg>
          <span class="control-label">上一个</span>
        </button>
        <button class="control-btn" @click="toggleFullscreen">
          <svg v-if="!isFullscreen" viewBox="0 0 24 24" width="22" height="22">
            <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="22" height="22">
            <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span class="control-label">{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </button>
        <button class="control-btn" @click="nextChannel" :disabled="!hasNext">
          <svg viewBox="0 0 24 24" width="22" height="22">
            <polygon points="5 4 15 12 5 20 5 4" fill="none" stroke="currentColor" stroke-width="2"/>
            <line x1="19" y1="4" x2="19" y2="20" stroke="currentColor" stroke-width="2"/>
          </svg>
          <span class="control-label">下一个</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ezvizApi } from '@/api'
import EZUIKit from 'ezuikit-js'

const router = useRouter()

const initialized = ref(false)
const error = ref('')
const isFullscreen = ref(false)
const controlsVisible = ref(true)
const loadMsg = ref('加载中...')
const channels = ref([])

const playPageRef = ref(null)
const playerAreaRef = ref(null)

let playerInstance = null
let currentToken = null
let controlsTimer = null
let touchStartX = 0
let touchStartY = 0
let touchStartTime = 0

const SWIPE_THRESHOLD = 50

// 从 sessionStorage 读取选中的通道
const selectedChannel = ref(null)

const channelName = computed(() => {
  return selectedChannel.value?.channelName || '通道' + (selectedChannel.value?.channelNo || '')
})

const channelMeta = computed(() => {
  if (!selectedChannel.value) return ''
  return (selectedChannel.value.deviceName || selectedChannel.value.deviceSerial) + ' · CH' + selectedChannel.value.channelNo
})

// 获取当前索引
const currentIndex = computed(() => {
  return channels.value.findIndex(c =>
    c.deviceSerial === selectedChannel.value?.deviceSerial &&
    c.channelNo === selectedChannel.value?.channelNo
  )
})

const hasPrev = computed(() => currentIndex.value > 0)
const hasNext = computed(() => currentIndex.value >= 0 && currentIndex.value < channels.value.length - 1)

// 播放地址
const playUrl = computed(() => {
  if (!selectedChannel.value) return ''
  return `ezopen://open.ys7.com/${selectedChannel.value.deviceSerial}/${selectedChannel.value.channelNo}.hd.live`
})

function goBack() {
  router.push('/mobile/dashboard')
}

function showControls() {
  controlsVisible.value = true
  clearTimeout(controlsTimer)
  controlsTimer = setTimeout(() => {
    controlsVisible.value = false
  }, 4000)
}

function toggleControls() {
  if (controlsVisible.value) {
    controlsVisible.value = false
    clearTimeout(controlsTimer)
  } else {
    showControls()
  }
}

// 切换通道
function switchToChannel(ch) {
  if (!ch) return
  selectedChannel.value = ch
  sessionStorage.setItem('mobileSelectedChannel', JSON.stringify(ch))
  destroyPlayer()
  initPlayer()
}

function prevChannel() {
  const idx = currentIndex.value
  if (idx > 0) {
    switchToChannel(channels.value[idx - 1])
  }
}

function nextChannel() {
  const idx = currentIndex.value
  if (idx >= 0 && idx < channels.value.length - 1) {
    switchToChannel(channels.value[idx + 1])
  }
}

// 全屏切换
function toggleFullscreen() {
  const el = playPageRef.value
  if (!el) return

  if (!document.fullscreenElement) {
    if (el.requestFullscreen) {
      el.requestFullscreen().then(() => {
        // 全屏后锁定竖屏方向
        if (screen.orientation && screen.orientation.lock) {
          screen.orientation.lock('natural').catch(() => {})
        }
      }).catch(() => {})
    } else if (el.webkitRequestFullscreen) {
      el.webkitRequestFullscreen()
    }
    isFullscreen.value = true
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {})
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen()
    }
    isFullscreen.value = false
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  document.body.classList.toggle('video-fullscreen', isFullscreen.value)
}

// 手势处理
function onTouchStart(e) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchStartTime = Date.now()
}

function onTouchEnd(e) {
  const endX = e.changedTouches[0].clientX
  const deltaX = endX - touchStartX
  const elapsed = Date.now() - touchStartTime

  // 短时间内大幅度滑动才触发切换
  if (Math.abs(deltaX) >= SWIPE_THRESHOLD && elapsed < 500) {
    if (deltaX > 0) {
      prevChannel()
    } else {
      nextChannel()
    }
  } else if (Math.abs(deltaX) < 10 && elapsed < 300) {
    // 点击切换控制栏显示
    toggleControls()
  }

  // 有操作时重新显示控制栏
  showControls()
}

// 初始化播放器
async function initPlayer() {
  if (!selectedChannel.value) {
    error.value = '未选择通道'
    return
  }

  error.value = ''
  initialized.value = false
  loadMsg.value = '连接中...'

  try {
    // 获取token
    loadMsg.value = '获取凭证...'
    const tokenRes = await ezvizApi.getToken()
    currentToken = tokenRes.data.data.accessToken

    loadMsg.value = '加载视频流...'

    playerInstance = new EZUIKit.EZUIKitPlayer({
      id: 'mobile-ezuikit-player',
      accessToken: currentToken,
      url: playUrl.value,
      template: 'mobileLive',
      autoplay: true,
      staticPath: '/ezuikit_cdn',
      scaleMode: 1,
      audio: 0,
      width: '100%',
      height: '100%',
      videoLevel: localStorage.getItem('videoQuality') === 'sd' ? 'sd' : localStorage.getItem('videoQuality') === 'fluent' ? 'fluent' : 'hd',
      handleSuccess: () => {
        initialized.value = true
        showControls()
      },
      handleError: (err) => {
        const code = err?.code || ''
        const errorMap = {
          '395404': '设备不在线',
          '395406': 'Token已过期，请刷新页面',
          '395415': '设备通道错误',
          '399001': '网络超时',
          '3820032': '通道不存在',
          '10001': '视频流协议错误',
        }
        error.value = errorMap[String(code)] || (err?.msg || err?.message || '播放失败')
        initialized.value = false
      }
    })
  } catch (e) {
    error.value = '连接失败，请重试'
    initialized.value = false
  }
}

function destroyPlayer() {
  if (playerInstance) {
    try {
      playerInstance.stop()
      playerInstance.destroy()
    } catch (e) {}
    playerInstance = null
  }
}

onMounted(async () => {
  // 读取选中通道
  const saved = sessionStorage.getItem('mobileSelectedChannel')
  if (saved) {
    try {
      selectedChannel.value = JSON.parse(saved)
    } catch (e) {}
  }

  // 加载所有通道（用于切换）
  try {
    const res = await ezvizApi.getAllChannels()
    channels.value = res.data.data || []
  } catch (e) {
    console.error('加载通道列表失败', e)
  }

  // 如果没有选中通道但列表不为空，选择第一个在线通道
  if (!selectedChannel.value && channels.value.length > 0) {
    const online = channels.value.find(c => c.status === 'online')
    selectedChannel.value = online || channels.value[0]
    sessionStorage.setItem('mobileSelectedChannel', JSON.stringify(selectedChannel.value))
  }

  if (selectedChannel.value) {
    initPlayer()
  } else {
    error.value = '暂无可播放的通道'
  }

  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  destroyPlayer()
  clearTimeout(controlsTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  document.body.classList.remove('video-fullscreen')
})
</script>

<style scoped>
.mobile-play {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #000;
  overflow: hidden;
  position: relative;
}

.player-area {
  flex: 1;
  position: relative;
  background: #000;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ezuikit-player {
  width: 100%;
  height: 100%;
  position: absolute;
  inset: 0;
  z-index: 1;
}

/* 全屏模式 */
.mobile-play:fullscreen,
.mobile-play:-webkit-full-screen {
  width: 100vw;
  height: 100vh;
  background: #000;
}

/* 加载状态 */
.player-loading {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #000;
  color: #94a3b8;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  position: relative;
}

.loading-spinner::before {
  content: '';
  position: absolute;
  inset: 0;
  border: 3px solid transparent;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

.loading-text {
  font-size: 13px;
  color: #e2e8f0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 错误状态 */
.player-error {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #000;
  color: #94a3b8;
}

.error-text {
  color: #f87171;
  font-size: 13px;
  text-align: center;
  padding: 0 20px;
}

.retry-btn {
  padding: 10px 28px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 4px;
}

.retry-btn:active {
  background: #2563eb;
}

/* 顶部操作栏 */
.player-top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  padding-top: calc(8px + env(safe-area-inset-top, 0));
  background: linear-gradient(180deg, rgba(0,0,0,0.6) 0%, rgba(0,0,0,0) 100%);
  /* 初始隐藏，触摸显示 */
  opacity: 0;
  transition: opacity 0.3s;
}

.player-bottom-bar.visible ~ .player-top-bar,
.player-area:active .player-top-bar,
.player-top-bar:hover {
  opacity: 1;
}

/* 使用兄弟选择器不太好，用js控制class */
.player-top-bar {
  opacity: 1;
}

.top-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.15);
  border: none;
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
}

.top-btn:active {
  background: rgba(255,255,255,0.25);
}

.top-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.top-channel-name {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-channel-meta {
  font-size: 11px;
  color: rgba(255,255,255,0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-spacer {
  width: 36px;
  flex-shrink: 0;
}

/* 底部控制栏 */
.player-bottom-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0));
  background: linear-gradient(0deg, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0) 100%);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.player-bottom-bar.visible {
  opacity: 1;
  pointer-events: auto;
}

.control-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: none;
  color: #fff;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  min-width: 60px;
}

.control-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.control-btn:active:not(:disabled) {
  opacity: 0.7;
}

.control-label {
  font-size: 10px;
  font-weight: 500;
  color: rgba(255,255,255,0.7);
}
</style>
