<template>
  <div class="video-player-wrapper">
    <!-- 加载中 -->
    <div v-if="!initialized && !error && !isRetrying" class="player-loading">
      <div class="loading-spinner">
        <svg class="spinner-ring" viewBox="0 0 50 50">
          <circle cx="25" cy="25" r="20" fill="none" stroke-width="3"/>
        </svg>
      </div>
      <p class="loading-text">{{ loadMessage }}</p>
      <div class="loading-progress">
        <span class="progress-dot" :class="{ active: loadStep >= 1 }"></span>
        <span class="progress-line"></span>
        <span class="progress-dot" :class="{ active: loadStep >= 2 }"></span>
        <span class="progress-line"></span>
        <span class="progress-dot" :class="{ active: loadStep >= 3 }"></span>
      </div>
      <p class="loading-hint">获取凭证 → 连接服务器 → 加载视频</p>
    </div>

    <!-- 正在自动重试 -->
    <div v-if="isRetrying" class="player-loading">
      <div class="loading-spinner retry-spinner">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#f59e0b" stroke-width="2">
          <polyline points="1 4 1 10 7 10"/>
          <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
        </svg>
      </div>
      <p class="loading-text retry-text">连接断开，正在重连 ({{ retryCount }}/{{ MAX_RETRIES }})</p>
      <p class="loading-hint">网络较慢，正在为您保持连接...</p>
    </div>

    <!-- 画质切换中的过渡效果 -->
    <div v-if="isSwitchingQuality" class="player-loading quality-switching-overlay">
      <div class="loading-spinner">
        <svg class="spinner-ring" viewBox="0 0 50 50">
          <circle cx="25" cy="25" r="20" fill="none" stroke-width="3"/>
        </svg>
      </div>
      <p class="loading-text">切换画质中...</p>
    </div>

    <!-- 播放失败 -->
    <div v-if="error && !isRetrying && !isSwitchingQuality" class="player-error">
      <svg viewBox="0 0 80 80" width="56" height="56">
        <circle cx="40" cy="40" r="30" fill="none" stroke="#f87171" stroke-width="3"/>
        <line x1="28" y1="28" x2="52" y2="52" stroke="#f87171" stroke-width="3"/>
        <line x1="52" y1="28" x2="28" y2="52" stroke="#f87171" stroke-width="3"/>
      </svg>
      <p class="error-text">{{ error }}</p>
      <p class="error-hint">请检查摄像头电源和网络连接，然后重试</p>
      <button class="btn-retry" @click="initPlayer">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
        重新连接
      </button>
    </div>

    <!-- 播放器容器 -->
    <div id="ezuikit-player" class="ezuikit-player" v-show="!error"></div>

    <!-- 画质切换按钮 -->
    <div v-if="initialized && !isSwitchingQuality" class="quality-toggle">
      <button
        class="q-btn"
        :class="{ active: quality === 'sd' }"
        @click="switchQuality('sd')"
        title="流畅模式（低码率，适合网速慢的情况）"
      >流畅</button>
      <button
        class="q-btn"
        :class="{ active: quality === 'hd' }"
        @click="switchQuality('hd')"
        title="高清模式（高码率，需要良好网络）"
      >高清</button>
      <button
        class="q-btn auto-btn"
        :class="{ active: quality === 'auto' }"
        @click="switchQuality('auto')"
        title="自动模式（根据网络状况自适应）"
      >自动</button>
    </div>

    <!-- 网速指示器 -->
    <div v-if="initialized && !isSwitchingQuality" class="network-indicator" :class="networkQuality">
      <span class="ni-dot"></span>
      <span class="ni-label">{{ networkLabel }}</span>
    </div>

    <!-- PTZ 方向控制面板 (仅当设备支持云台时显示) -->
    <div v-if="channel.ptzSupported && initialized && !isSwitchingQuality" class="ptz-panel">
      <div class="ptz-dpad">
        <!-- 上 -->
        <button
          class="ptz-btn ptz-up"
          @mousedown.prevent="startPtz(PTZ_DIR.UP)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.UP)"
          @touchend="stopPtz"
          title="向上"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 5l-7 7h4v5h6v-5h4L12 5z" fill="currentColor"/></svg>
        </button>

        <!-- 左 -->
        <button
          class="ptz-btn ptz-left"
          @mousedown.prevent="startPtz(PTZ_DIR.LEFT)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.LEFT)"
          @touchend="stopPtz"
          title="向左"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><path d="M19 12H5m0 0l5-5m-5 5l5 5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>
        </button>

        <!-- 中心/复位指示 -->
        <div class="ptz-center">
          <svg viewBox="0 0 24 24" width="14" height="14"><circle cx="12" cy="12" r="4" fill="currentColor" opacity="0.6"/></svg>
        </div>

        <!-- 右 -->
        <button
          class="ptz-btn ptz-right"
          @mousedown.prevent="startPtz(PTZ_DIR.RIGHT)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.RIGHT)"
          @touchend="stopPtz"
          title="向右"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><path d="M5 12h14m0 0l-5-5m5 5l-5 5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>
        </button>

        <!-- 下 -->
        <button
          class="ptz-btn ptz-down"
          @mousedown.prevent="startPtz(PTZ_DIR.DOWN)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.DOWN)"
          @touchend="stopPtz"
          title="向下"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 19l7-7h-4V7h-6v5H5l7 7z" fill="currentColor"/></svg>
        </button>
      </div>

      <!-- 变焦控制 -->
      <div class="ptz-zoom">
        <button
          class="ptz-btn ptz-zoom-in"
          @mousedown.prevent="startPtz(PTZ_DIR.ZOOM_IN)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.ZOOM_IN)"
          @touchend="stopPtz"
          title="变焦放大"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2" fill="none"/><line x1="11" y1="8" x2="11" y2="14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><line x1="8" y1="11" x2="14" y2="11" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        </button>
        <span class="ptz-zoom-label">ZOOM</span>
        <button
          class="ptz-btn ptz-zoom-out"
          @mousedown.prevent="startPtz(PTZ_DIR.ZOOM_OUT)"
          @mouseup="stopPtz"
          @mouseleave="stopPtz"
          @touchstart.prevent="startPtz(PTZ_DIR.ZOOM_OUT)"
          @touchend="stopPtz"
          title="变焦缩小"
        >
          <svg viewBox="0 0 24 24" width="18" height="18"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2" fill="none"/><line x1="8" y1="11" x2="14" y2="11" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ezvizApi } from '@/api'
import EZUIKit from 'ezuikit-js'

const props = defineProps({
  channel: { type: Object, required: true },
  cachedToken: { type: String, default: null },
  appKey: { type: String, default: '' }
})

const PTZ_DIR = ezvizApi.PtzDirection
const MAX_RETRIES = 3         // 增加到3次重试

const loading = ref(true)
const initialized = ref(false)
const error = ref('')
const ptzActive = ref(false)
const quality = ref('auto')
const loadStep = ref(0)
const loadTimeout = ref(null)
const retryCount = ref(0)
const isRetrying = ref(false)
const isSwitchingQuality = ref(false)
const networkQuality = ref('good')

let playerInstance = null
let currentToken = null
let networkMonitor = null

// ====== 核心性能优化参数 ======
const CONNECT_TIMEOUT = 30000     // 30秒（原45秒，减少等待时间，加快失败重试）
const VIDEO_BUFFER_SD = 5         // 流畅模式：5秒缓冲区（网速慢时更流畅）
const VIDEO_BUFFER_HD = 2         // 高清模式：2秒缓冲区
const VIDEO_BUFFER_AUTO = 3       // 自动模式：3秒缓冲区
const RETRY_BASE_DELAY = 1000     // 初始重试延迟 1秒
const RETRY_MAX_DELAY = 8000      // 最大重试延迟 8秒

// ====== 网络质量检测 ======
const networkLabel = computed(() => ({
  good: '网络良好',
  fair: '网络一般',
  poor: '网络较差'
}[networkQuality.value] || '网络良好'))

/**
 * 检测网络连接质量（通过测量延迟）
 */
function checkNetworkQuality() {
  const start = performance.now()
  return fetch('/api/health', { method: 'GET', cache: 'no-store' })
    .then(() => {
      const latency = performance.now() - start
      if (latency < 200) networkQuality.value = 'good'
      else if (latency < 500) networkQuality.value = 'fair'
      else networkQuality.value = 'poor'
    })
    .catch(() => {
      networkQuality.value = 'poor'
    })
}

/**
 * 根据当前网络质量和画质选择决定视频缓冲区大小
 */
function getOptimalBuffer() {
  if (quality.value === 'hd') return VIDEO_BUFFER_HD
  if (quality.value === 'sd') return VIDEO_BUFFER_SD
  // auto 模式：根据网络质量动态调整
  if (networkQuality.value === 'poor') return 5
  if (networkQuality.value === 'fair') return 3
  return VIDEO_BUFFER_AUTO
}

const playUrl = computed(() => {
  // auto 模式默认使用 sd（流畅），如果网络好可以在播放后动态提升
  const actualQuality = quality.value === 'auto' ? 'sd' : quality.value
  return `ezopen://open.ys7.com/${props.channel.deviceSerial}/${props.channel.channelNo}.${actualQuality}.live`
})

const loadMessage = computed(() => {
  const msgs = {
    0: '准备中...',
    1: '获取凭证...',
    2: '连接服务器...',
    3: '加载视频流...',
  }
  return msgs[loadStep.value] || '连接中...'
})

/** 指数退避延迟计算 */
function getRetryDelay(attempt) {
  const delay = Math.min(RETRY_BASE_DELAY * Math.pow(2, attempt), RETRY_MAX_DELAY)
  return delay + Math.random() * 1000  // 加抖动防止同时重试
}

function startLoadTimeout(timeout) {
  clearLoadTimeout()
  loadTimeout.value = setTimeout(() => {
    if (!initialized.value) {
      handleConnectTimeout()
    }
  }, timeout || CONNECT_TIMEOUT)
}

function clearLoadTimeout() {
  if (loadTimeout.value) {
    clearTimeout(loadTimeout.value)
    loadTimeout.value = null
  }
}

/** 连接超时处理 - 指数退避自动重试 */
async function handleConnectTimeout() {
  if (retryCount.value < MAX_RETRIES) {
    retryCount.value++
    isRetrying.value = true
    loading.value = true
    error.value = ''

    destroyPlayerInstance()

    // 指数退避延迟
    const delay = getRetryDelay(retryCount.value)
    await new Promise(r => setTimeout(r, delay))
    isRetrying.value = false

    // 重试前检测网络质量
    await checkNetworkQuality()
    startLoadTimeout(CONNECT_TIMEOUT + retryCount.value * 5000)
    createPlayer()
  } else {
    error.value = '连接超时，请检查网络后重试'
    loading.value = false
  }
}

function destroyPlayerInstance() {
  if (playerInstance) {
    try {
      playerInstance.stop()
      playerInstance.destroy()
    } catch (e) { /* ignore */ }
    playerInstance = null
  }
}

/** 创建播放器实例 */
function createPlayer() {
  if (!currentToken) {
    error.value = '无法获取播放凭证，请刷新页面后重试'
    loading.value = false
    return
  }

  loadStep.value = 3

  const plugins = []
  if (props.channel.talkSupported) plugins.push('talk')

  const bufferSize = getOptimalBuffer()

  playerInstance = new EZUIKit.EZUIKitPlayer({
    id: 'ezuikit-player',
    accessToken: currentToken,
    url: playUrl.value,
    template: 'security',
    plugin: plugins,
    width: '100%',
    height: '100%',
    videoBuffer: bufferSize,
    handleSuccess: () => {
      clearLoadTimeout()
      initialized.value = true
      loading.value = false
      retryCount.value = 0
      isRetrying.value = false
      isSwitchingQuality.value = false
      loadStep.value = 0
    },
    handleError: (err) => {
      clearLoadTimeout()
      const code = err?.code || ''
      const msg = err?.msg || err?.message || '未知错误'
      const errorMap = {
        '395404': '设备不在线',
        '395406': 'Token已过期，请刷新页面',
        '395415': '设备通道错误',
        '399001': '网络超时，正在重试...',
        '3820032': '通道不存在',
      }
      const errorMsg = errorMap[String(code)] || `播放失败(${code}): ${msg}`

      // 网络超时也触发自动重试
      if (String(code) === '399001' && retryCount.value < MAX_RETRIES) {
        handleConnectTimeout()
      } else {
        error.value = errorMsg
        loading.value = false
        isSwitchingQuality.value = false
      }
    }
  })
}

async function initPlayer() {
  loading.value = true
  error.value = ''
  initialized.value = false
  retryCount.value = 0
  isRetrying.value = false
  isSwitchingQuality.value = false
  loadStep.value = 1

  // 先检测网络质量
  await checkNetworkQuality()

  startLoadTimeout(CONNECT_TIMEOUT)

  try {
    // 获取 accessToken（优先使用缓存的token）
    currentToken = props.cachedToken
    if (!currentToken) {
      loadStep.value = 1
      const tokenRes = await ezvizApi.getToken()
      currentToken = tokenRes.data.data.accessToken
    }

    loadStep.value = 2
    loadStep.value = 3
    createPlayer()
  } catch (err) {
    clearLoadTimeout()
    currentToken = null
    error.value = '连接失败: ' + (err.response?.data?.message || err.message)
    loading.value = false
    isSwitchingQuality.value = false
  }
}

/**
 * 切换画质 - 使用更平滑的过渡
 * 保留 currentToken 避免重新获取Token，减少切换时间
 */
async function switchQuality(q) {
  if (quality.value === q && !isSwitchingQuality.value) return
  quality.value = q
  isSwitchingQuality.value = true

  // 先销毁旧的播放器
  destroyPlayerInstance()

  // 在切换过程中保留播放器容器可见，显示画质切换提示
  // 小延迟让UI过渡更平滑
  await new Promise(r => setTimeout(r, 300))

  // 使用现有的 currentToken，不再重新获取
  startLoadTimeout(15000) // 画质切换用更短的超时
  createPlayer()
}

/** 启动 PTZ 云台控制 */
async function startPtz(direction) {
  if (ptzActive.value) return
  ptzActive.value = true
  try {
    await ezvizApi.startPtz(props.channel.deviceSerial, props.channel.channelNo, direction)
  } catch (err) {
    console.error('PTZ控制失败', err)
  }
}

/** 停止 PTZ 云台转动 */
async function stopPtz() {
  if (!ptzActive.value) return
  ptzActive.value = false
  try {
    await ezvizApi.stopPtz(props.channel.deviceSerial, props.channel.channelNo)
  } catch (err) {
    console.error('PTZ停止失败', err)
  }
}

function destroyPlayer() {
  if (ptzActive.value) stopPtz()
  destroyPlayerInstance()
}

onMounted(() => {
  initPlayer()
  // 启动网络质量周期性检测（每30秒检查一次）
  networkMonitor = setInterval(checkNetworkQuality, 30000)
})

onUnmounted(() => {
  if (networkMonitor) {
    clearInterval(networkMonitor)
    networkMonitor = null
  }
  destroyPlayer()
})

watch(() => props.channel.id + '-' + props.channel.channelNo, () => {
  destroyPlayer()
  // 切换通道时重置画质为 auto
  quality.value = 'auto'
  initPlayer()
})
</script>

<style scoped>
.video-player-wrapper {
  width: 100%; height: 100%;
  position: relative;
  background: #0f0f1a;
  border-radius: 8px;
  overflow: hidden;
}
.ezuikit-player {
  width: 100%; height: 100%; min-height: 300px;
}
.player-loading, .player-error {
  position: absolute; inset: 0; z-index: 10;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px; background: #0f0f1a; color: #94a3b8;
}

/* 画质切换覆盖层 */
.quality-switching-overlay {
  z-index: 15;
  background: rgba(15, 15, 26, 0.85);
  animation: fadeIn 0.2s ease;
}

/* 加载动画 */
.loading-spinner {
  width: 48px; height: 48px;
  position: relative;
}

.spinner-ring {
  width: 100%; height: 100%;
  animation: spin 1s linear infinite;
}

.spinner-ring circle {
  cx: 25; cy: 25; r: 20;
  fill: none;
  stroke: url(#gradient);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-dasharray: 90;
  stroke-dashoffset: 0;
}

.loading-spinner::before,
.loading-spinner::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid transparent;
}

.loading-spinner::before {
  border-top-color: var(--primary, #3b82f6);
  border-right-color: rgba(59, 130, 246, 0.3);
  animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

.loading-spinner::after {
  border-bottom-color: rgba(59, 130, 246, 0.1);
  border-left-color: rgba(59, 130, 246, 0.05);
  animation: spin 1.2s cubic-bezier(0.4, 0, 0.2, 1) infinite reverse;
}

.loading-text {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
  animation: pulse-text 1.5s ease-in-out infinite;
}

.loading-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.progress-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  transition: all 0.3s;
}

.progress-dot.active {
  background: var(--primary, #3b82f6);
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
}

.progress-line {
  width: 24px;
  height: 2px;
  border-radius: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.loading-hint {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  margin-top: 2px;
  letter-spacing: 0.5px;
}

.player-error .error-text { color: #f87171; font-size: 14px; max-width: 350px; text-align: center; }
.player-error .error-hint { color: rgba(255,255,255,0.4); font-size: 12px; margin-top: -4px; }

.btn-retry {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 28px;
  background: var(--primary, #3b82f6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 4px;
}

.btn-retry:hover {
  background: var(--primary-dark, #2563eb);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-retry:active {
  transform: translateY(0);
}

.retry-spinner {
  animation: pulse-retry 1.5s ease-in-out infinite;
}

.retry-text {
  color: #f59e0b !important;
}

@keyframes pulse-retry {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

/* ========== 画质切换 ========== */
.quality-toggle {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 20;
  display: flex;
  gap: 2px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border-radius: 8px;
  padding: 3px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  opacity: 0;
  transform: translateY(-8px);
  animation: ptzFadeIn 0.4s ease-out 0.5s forwards;
}

.q-btn {
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 700;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all 0.2s;
  letter-spacing: 0.5px;
}

.q-btn:hover {
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.08);
}

.q-btn.active {
  background: rgba(59, 130, 246, 0.4);
  color: #fff;
  box-shadow: 0 1px 4px rgba(59, 130, 246, 0.2);
}

.auto-btn.active {
  background: rgba(16, 185, 129, 0.4) !important;
}

/* ========== 网速指示器 ========== */
.network-indicator {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.3px;
  opacity: 0;
  animation: ptzFadeIn 0.4s ease-out 0.8s forwards;
}

.ni-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.network-indicator.good .ni-dot {
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
}
.network-indicator.good .ni-label {
  color: #86efac;
}

.network-indicator.fair .ni-dot {
  background: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}
.network-indicator.fair .ni-label {
  color: #fcd34d;
}

.network-indicator.poor .ni-dot {
  background: #ef4444;
  box-shadow: 0 0 6px rgba(239, 68, 68, 0.5);
}
.network-indicator.poor .ni-label {
  color: #fca5a5;
}

/* ========== PTZ 方向控制面板 ========== */
.ptz-panel {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  opacity: 0;
  transform: translateY(8px);
  animation: ptzFadeIn 0.4s ease-out 0.3s forwards;
  pointer-events: none;
}
.ptz-panel > * {
  pointer-events: auto;
}

.ptz-dpad {
  display: grid;
  grid-template-columns: 44px 44px 44px;
  grid-template-rows: 44px 44px 44px;
  gap: 2px;
  justify-items: center;
  align-items: center;
}

.ptz-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  border: 1px solid rgba(255, 255, 255, 0.12);
  user-select: none;
  -webkit-user-select: none;
  touch-action: manipulation;
}

.ptz-btn:hover {
  background: rgba(59, 130, 246, 0.35);
  border-color: rgba(59, 130, 246, 0.5);
  color: #fff;
  transform: scale(1.08);
}

.ptz-btn:active {
  background: rgba(59, 130, 246, 0.5);
  border-color: rgba(59, 130, 246, 0.7);
  transform: scale(0.95);
}

.ptz-up    { grid-column: 2; grid-row: 1; }
.ptz-left  { grid-column: 1; grid-row: 2; }
.ptz-center { grid-column: 2; grid-row: 2; width: 32px; height: 32px; border-radius: 50%; background: rgba(255,255,255,0.05); border-color: transparent; cursor: default; }
.ptz-right { grid-column: 3; grid-row: 2; }
.ptz-down  { grid-column: 2; grid-row: 3; }

.ptz-zoom {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border-radius: 10px;
  padding: 3px 6px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.ptz-zoom .ptz-btn {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: transparent;
  border: none;
}

.ptz-zoom .ptz-btn:hover {
  background: rgba(59, 130, 246, 0.3);
}

.ptz-zoom-label {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.5);
  writing-mode: vertical-lr;
  text-orientation: mixed;
  padding: 2px 0;
}

@keyframes ptzFadeIn {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse-text {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>
