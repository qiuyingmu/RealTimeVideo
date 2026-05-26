<template>
  <div class="video-player-wrapper" ref="playerWrapperRef">
    <!-- 通道切换过渡 -->
    <div v-if="isSwitchingChannel" class="player-loading channel-switching-overlay">
      <div class="loading-spinner">
        <svg class="spinner-ring" viewBox="0 0 50 50">
          <circle cx="25" cy="25" r="20" fill="none" stroke-width="3"/>
        </svg>
      </div>
      <p class="loading-text">切换通道...</p>
    </div>

    <!-- 加载中 -->
    <div v-if="!initialized && !error && !isRetrying && !isSwitchingQuality && !isSwitchingChannel" class="player-loading">
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

    <!-- 画质切换过渡 -->
    <div v-if="isSwitchingQuality" class="player-loading quality-switching-overlay">
      <div class="loading-spinner">
        <svg class="spinner-ring" viewBox="0 0 50 50">
          <circle cx="25" cy="25" r="20" fill="none" stroke-width="3"/>
        </svg>
      </div>
      <p class="loading-text">切换画质中...</p>
    </div>

    <!-- 正在重连 -->
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

    <!-- 播放失败 -->
    <div v-if="error && !isRetrying && !isSwitchingQuality && !isSwitchingChannel" class="player-error">
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

    <!-- 播放器容器（EZUIKit security模板内置全部控件） -->
    <div id="ezuikit-player" class="ezuikit-player" v-show="!error"></div>

    <!-- 网速指示器 -->
    <div v-if="initialized && !isSwitchingQuality && !isRetrying && !isSwitchingChannel" class="network-indicator" :class="networkQuality">
      <span class="ni-dot"></span>
      <span class="ni-label">{{ networkLabel }}</span>
    </div>

    <!-- ====== 移动端 - 仅保留频道切换按钮 ======
         EZUIKit mobileLive 模板自带底部工具栏（PTZ/全屏/画质等），
         我们只补充 EZUIKit 不提供的「上一个/下一个通道」导航按钮。 -->
    <div v-if="initialized && isMobile && !isSwitchingChannel" class="mobile-nav-overlay">
      <button v-if="showMobileControls" class="mobile-nav-btn mobile-nav-left" @click.stop="emitPrevChannel" aria-label="上一个通道">
        <svg viewBox="0 0 24 24" width="22" height="22"><polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
      <button v-if="showMobileControls" class="mobile-nav-btn mobile-nav-right" @click.stop="emitNextChannel" aria-label="下一个通道">
        <svg viewBox="0 0 24 24" width="22" height="22"><polyline points="9 6 15 12 9 18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ezvizApi } from '@/api'
import EZUIKit from 'ezuikit-js'

const props = defineProps({
  channel: { type: Object, required: true },
  cachedToken: { type: String, default: null },
  appKey: { type: String, default: '' }
})

const emit = defineEmits(['prev-channel', 'next-channel'])

const MAX_RETRIES = 3
const SWIPE_THRESHOLD = 50

const loading = ref(true)
const initialized = ref(false)
const error = ref('')
const loadStep = ref(0)
const loadTimeout = ref(null)
const retryCount = ref(0)
const isRetrying = ref(false)
const isSwitchingQuality = ref(false)
const isSwitchingChannel = ref(false)
const networkQuality = ref('good')
const isFullscreen = ref(false)
const showMobileControls = ref(false)
const isMobile = ref(false)

// 缩放模式：0=拉伸填充, 1=等比缩放(含黑边), 2=裁剪填充（移动端默认裁剪填充，铺满全屏且不变形）
const scaleMode = ref(parseInt(localStorage.getItem('mobileScaleMode') || '2'))

const SCALE_MODES = ['fill', 'fit', 'cover']
const scaleModeLabel = computed(() => ({
  fill: '拉伸填充', fit: '等比缩放', cover: '裁剪填充'
}[SCALE_MODES[scaleMode.value]] || '拉伸填充'))

const playerWrapperRef = ref(null)

let playerInstance = null
let currentToken = null
let networkMonitor = null
let isInitializing = false
let controlsHideTimer = null
let touchStartX = 0
let touchStartY = 0
let latestChannelId = '' // 用于判断 channel 是否真正变化

const CONNECT_TIMEOUT = 30000
const RETRY_BASE_DELAY = 1000
const RETRY_MAX_DELAY = 8000

const networkLabel = computed(() => ({
  good: '网络良好', fair: '网络一般', poor: '网络较差'
}[networkQuality.value] || '网络良好'))

const loadMessage = computed(() => {
  const msgs = { 0: '准备中...', 1: '获取凭证...', 2: '连接服务器...', 3: '加载视频流...' }
  return msgs[loadStep.value] || '连接中...'
})

// ====== 网络质量检测 ======
function checkNetworkQuality() {
  const start = performance.now()
  return fetch('/api/health', { method: 'GET', cache: 'no-store' })
    .then(() => {
      const latency = performance.now() - start
      if (latency < 200) networkQuality.value = 'good'
      else if (latency < 500) networkQuality.value = 'fair'
      else networkQuality.value = 'poor'
    })
    .catch(() => { networkQuality.value = 'poor' })
}

// ====== 播放地址 ======
const playUrl = computed(() => {
  const quality = localStorage.getItem('videoQuality') || 'hd'
  const url = `ezopen://open.ys7.com/${props.channel.deviceSerial}/${props.channel.channelNo}.${quality}.live`
  if (import.meta.env.DEV) {
    console.log('[VideoPlayer] URL:',
      url,
      '| deviceSerial:', props.channel.deviceSerial,
      '| chNo:', props.channel.channelNo,
      '| quality:', quality)
  }
  return url
})

// ====== 超时/重试逻辑 ======
function getRetryDelay(attempt) {
  return Math.min(RETRY_BASE_DELAY * Math.pow(2, attempt), RETRY_MAX_DELAY) + Math.random() * 1000
}

function startLoadTimeout(timeout) {
  clearLoadTimeout()
  loadTimeout.value = setTimeout(() => { if (!initialized.value) handleConnectTimeout() }, timeout || CONNECT_TIMEOUT)
}
function clearLoadTimeout() {
  if (loadTimeout.value) { clearTimeout(loadTimeout.value); loadTimeout.value = null }
}

async function handleConnectTimeout() {
  if (retryCount.value < MAX_RETRIES) {
    retryCount.value++
    isRetrying.value = true; loading.value = true; error.value = ''
    destroyPlayerInstance()
    await new Promise(r => setTimeout(r, getRetryDelay(retryCount.value)))
    isRetrying.value = false
    await checkNetworkQuality()
    startLoadTimeout(CONNECT_TIMEOUT + retryCount.value * 5000)
    createPlayer()
  } else {
    error.value = '连接超时，请检查网络后重试'
    loading.value = false; isSwitchingChannel.value = false
  }
}

function destroyPlayerInstance() {
  if (playerInstance) {
    try { playerInstance.stop(); playerInstance.destroy() } catch (e) { /* ignore */ }
    playerInstance = null
  }
}

// ====== 创建播放器 ======
function createPlayer() {
  if (!currentToken) { error.value = '无法获取播放凭证，请刷新页面后重试'; loading.value = false; return }

  loadStep.value = 3

  const plugins = props.channel.talkSupported ? ['talk'] : []

  // 检测是否为移动端，选择合适模板
  const isMobileDevice = window.innerWidth <= 768 || 'ontouchstart' in window
  const playerTemplate = isMobileDevice ? 'mobileLive' : 'pcLive'

  playerInstance = new EZUIKit.EZUIKitPlayer({
    id: 'ezuikit-player',
    accessToken: currentToken,
    url: playUrl.value,
    template: playerTemplate,          // 根据设备自动选择模板
    autoplay: true,
    staticPath: '/ezuikit_cdn',
    scaleMode: scaleMode.value,          // 从 localStorage 读取用户偏好的缩放模式
    audio: 0,
    width: '100%',
    height: '100%',
    // 从用户偏好读取画质（默认高清）
    videoLevel: localStorage.getItem('videoQuality') || 'hd',
    videoLevelList: ['hd', 'sd', 'fluent'],
    mobileExtendOptions: {
      controls: ['ptzControl', 'fullScreen', 'hdSwitch'],
      showClose: false,
      showBack: false,
      showPTZ: true,
    },
    handleSuccess: () => {
      clearLoadTimeout()
      initialized.value = true; loading.value = false; retryCount.value = 0
      isRetrying.value = false; isSwitchingQuality.value = false; isSwitchingChannel.value = false; loadStep.value = 0
      // 确保播放器容器不被内部resize挤压
      stabilizePlayerContainer()
    },
    handleError: (err) => {
      clearLoadTimeout()
      const code = err?.code || ''
      const retryableCodes = ['399001', '10001']
      if (retryableCodes.includes(String(code)) && retryCount.value < MAX_RETRIES) {
        handleConnectTimeout()
      } else {
        const errorMap = {
          '395404': '设备不在线', '395406': 'Token已过期，请刷新页面',
          '395415': '设备通道错误', '399001': '网络超时',
          '3820032': '通道不存在', '10001': '视频流协议错误',
        }
        error.value = errorMap[String(code)] || `${(err?.msg || err?.message || '播放失败')}(${code})`
        loading.value = false; isSwitchingQuality.value = false; isSwitchingChannel.value = false
      }
    }
  })
}

async function initPlayer() {
  if (isInitializing) return
  isInitializing = true
  loading.value = true; error.value = ''; initialized.value = false
  retryCount.value = 0; isRetrying.value = false; isSwitchingQuality.value = false
  loadStep.value = 1

  await checkNetworkQuality()
  startLoadTimeout(CONNECT_TIMEOUT)

  try {
    currentToken = props.cachedToken
    if (!currentToken) {
      loadStep.value = 1
      currentToken = (await ezvizApi.getToken()).data.data.accessToken
    }
    loadStep.value = 2; loadStep.value = 3
    createPlayer()
  } catch (err) {
    clearLoadTimeout(); currentToken = null
    error.value = '连接失败: ' + (err.friendlyMessage || '网络异常，请重试')
    loading.value = false; isSwitchingQuality.value = false; isSwitchingChannel.value = false
  } finally {
    isInitializing = false
  }
}

function destroyPlayer() {
  destroyPlayerInstance()
}

// ====== 稳定播放器容器（防止被内部resize挤压） ======
let resizeObserver = null

function stabilizePlayerContainer() {
  // 用ResizeObserver监控播放器内部DOM变化，防止EZUIKit内部缩放挤压父容器
  const playerEl = document.getElementById('ezuikit-player')
  if (!playerEl) return

  // 如果有旧的observer先断开
  if (resizeObserver) {
    resizeObserver.disconnect()
  }

  resizeObserver = new ResizeObserver((entries) => {
    for (const entry of entries) {
      // 如果播放器内部元素试图缩小容器，锁定宽高
      const wrapper = playerWrapperRef.value
      if (wrapper) {
        // 父容器保持100%占据，防止播放器内部autoresize缩小
        const parent = wrapper.parentElement
        if (parent) {
          const parentRect = parent.getBoundingClientRect()
          // 只有当父容器有合理尺寸时才设置flex-basis，防止干扰
          if (parentRect.height > 100 && parentRect.width > 100) {
            // 确保wrapper撑满父容器
            wrapper.style.width = '100%'
            wrapper.style.height = '100%'
          }
        }
      }
    }
  })

  // 监视播放器内部元素
  resizeObserver.observe(playerEl)
  // 也监视父容器
  if (playerWrapperRef.value) {
    resizeObserver.observe(playerWrapperRef.value)
  }
}

// ====== 全屏控制 ======
function toggleFullscreen() {
  const el = playerWrapperRef.value
  if (!el) return

  if (!document.fullscreenElement) {
    if (el.requestFullscreen) {
      el.requestFullscreen().catch(() => {})
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
  // 在全屏/退出时通知父组件（用于隐藏header等）
  document.dispatchEvent(new CustomEvent('video-fullscreen-change', {
    detail: { isFullscreen: isFullscreen.value }
  }))
}

// ====== 移动端控制栏 ======
function showControlsTemporarily() {
  showMobileControls.value = true
  clearTimeout(controlsHideTimer)
  controlsHideTimer = setTimeout(() => {
    showMobileControls.value = false
  }, 4000)
}

function toggleControls() {
  if (showMobileControls.value) {
    showMobileControls.value = false
    clearTimeout(controlsHideTimer)
  } else {
    showControlsTemporarily()
  }
}

// ====== 缩放模式循环切换（0→1→2→0） ======
function cycleScaleMode() {
  scaleMode.value = (scaleMode.value + 1) % 3  // 0→1→2→0
  localStorage.setItem('mobileScaleMode', String(scaleMode.value))
  // 通知播放器实例更新缩放模式
  if (playerInstance && typeof playerInstance.setScaleMode === 'function') {
    playerInstance.setScaleMode(scaleMode.value)
  }
}

function emitPrevChannel() {
  emit('prev-channel')
  showControlsTemporarily()
}

function emitNextChannel() {
  emit('next-channel')
  showControlsTemporarily()
}

// ====== 手势控制 ======
function onTouchStart(e) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
}

function onTouchEnd(e) {
  const endX = e.changedTouches[0].clientX
  const deltaX = endX - touchStartX

  // 只处理水平滑动（忽略小幅度抖动）
  if (Math.abs(deltaX) >= SWIPE_THRESHOLD) {
    if (deltaX > 0) {
      emitPrevChannel()  // 右滑 → 上一个
    } else {
      emitNextChannel()  // 左滑 → 下一个
    }
  }

  showControlsTemporarily()
}

// ====== 横屏变化检测 ======
function checkOrientation() {
  // 触发全屏事件检查，用于父组件自动隐藏header
  document.dispatchEvent(new CustomEvent('video-orientation-change', {
    detail: { landscape: window.innerWidth > window.innerHeight }
  }))
}

// ====== 监听通道变化，优雅切换（防止 playerKey++ 暴力重建导致白闪） ======
watch(() => props.channel, (newChannel, oldChannel) => {
  if (!newChannel) return

  // 检查是否真正变化（不同设备/通道号才需要重建）
  const isRealChange = !oldChannel ||
    oldChannel.deviceSerial !== newChannel.deviceSerial ||
    oldChannel.channelNo !== newChannel.channelNo

  if (!isRealChange) return

  // 记录最新通道ID，防止重复触发
  const newId = newChannel.deviceSerial + '-' + newChannel.channelNo
  if (newId === latestChannelId) return
  latestChannelId = newId

  // 优雅切换：先标记切换状态（显示过渡覆盖层），再重建播放器
  isSwitchingChannel.value = true

  // 短暂延迟确保 DOM 更新覆盖层后再销毁播放器
  setTimeout(() => {
    destroyPlayerInstance()
    initPlayer()
  }, 50)
}, { immediate: false })

// ====== 生命周期 ======
onMounted(() => {
  isMobile.value = window.innerWidth <= 768

  initPlayer()
  networkMonitor = setInterval(checkNetworkQuality, 30000)

  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  window.addEventListener('orientationchange', checkOrientation)
  window.addEventListener('resize', checkOrientation)

  // 首次显示控制栏
  setTimeout(showControlsTemporarily, 1500)
})

onUnmounted(() => {
  if (networkMonitor) { clearInterval(networkMonitor); networkMonitor = null }
  clearTimeout(controlsHideTimer)
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  destroyPlayer()

  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  window.removeEventListener('orientationchange', checkOrientation)
  window.removeEventListener('resize', checkOrientation)
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
  position: relative;
  z-index: 1;
}

/* ====== 全屏模式 ====== */
.video-player-wrapper:fullscreen,
.video-player-wrapper:-webkit-full-screen {
  border-radius: 0;
  background: #000;
  width: 100vw; height: 100vh;
}
.video-player-wrapper:fullscreen .ezuikit-player,
.video-player-wrapper:-webkit-full-screen .ezuikit-player {
  min-height: 100vh;
}

/* ====== 加载 / 错误 / 重试 覆盖层 ====== */
.player-loading, .player-error {
  position: absolute; inset: 0; z-index: 10;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px; background: #0f0f1a; color: #94a3b8;
}
.quality-switching-overlay,
.channel-switching-overlay {
  z-index: 15; background: rgba(15, 15, 26, 0.85); animation: fadeIn 0.2s ease;
}

.loading-spinner { width: 48px; height: 48px; position: relative; }
.loading-spinner::before, .loading-spinner::after {
  content: ''; position: absolute; inset: 0; border-radius: 50%; border: 3px solid transparent;
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
.loading-text { font-size: 14px; font-weight: 600; color: #e2e8f0; animation: pulse-text 1.5s ease-in-out infinite; }
.retry-spinner { animation: pulse-retry 1.5s ease-in-out infinite; }
.retry-text { color: #f59e0b !important; }

.loading-progress { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.progress-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.15); transition: all 0.3s;
}
.progress-dot.active {
  background: var(--primary, #3b82f6);
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
}
.progress-line { width: 24px; height: 2px; border-radius: 1px; background: rgba(255, 255, 255, 0.1); }
.loading-hint { font-size: 11px; color: rgba(255, 255, 255, 0.3); margin-top: 2px; letter-spacing: 0.5px; }

.player-error .error-text { color: #f87171; font-size: 14px; max-width: 350px; text-align: center; }
.player-error .error-hint { color: rgba(255,255,255,0.4); font-size: 12px; margin-top: -4px; }

.btn-retry {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 28px; background: var(--primary, #3b82f6); color: #fff;
  border: none; border-radius: 8px; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: all 0.2s; margin-top: 4px;
}
.btn-retry:hover { background: var(--primary-dark, #2563eb); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3); }
.btn-retry:active { transform: translateY(0); }

/* ====== 网速指示器 ====== */
.network-indicator {
  position: absolute; top: 10px; right: 10px; z-index: 20;
  display: flex; align-items: center; gap: 5px;
  padding: 4px 10px; border-radius: 6px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 10px; font-weight: 600; letter-spacing: 0.3px;
  opacity: 0; animation: fadeSlideIn 0.4s ease-out 0.8s forwards;
}
.ni-dot { width: 6px; height: 6px; border-radius: 50%; }
.network-indicator.good .ni-dot { background: #22c55e; box-shadow: 0 0 6px rgba(34, 197, 94, 0.5); }
.network-indicator.good .ni-label { color: #86efac; }
.network-indicator.fair .ni-dot { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.5); }
.network-indicator.fair .ni-label { color: #fcd34d; }
.network-indicator.poor .ni-dot { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); }
.network-indicator.poor .ni-label { color: #fca5a5; }

/* ====== 移动端频道导航按钮（EZUIKit 不提供此功能） ====== */
.mobile-nav-overlay {
  position: absolute; inset: 0; z-index: 30;
  pointer-events: none;              /* 触摸穿透 → EZUIKit 原生控件可操作 */
  touch-action: none;
}

.mobile-nav-btn {
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 44px; height: 60px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  border: none; border-radius: 8px;
  color: #fff; cursor: pointer;
  transition: all 0.2s;
  pointer-events: auto;              /* 按钮可点击 */
  z-index: 31;
}
.mobile-nav-btn:active {
  background: rgba(0, 0, 0, 0.5);
}
.mobile-nav-left { left: 6px; }
.mobile-nav-right { right: 6px; }

/* ====== 移动端适配 ====== */
@media (max-width: 768px) {
  .video-player-wrapper {
    border-radius: 0;
    touch-action: manipulation;
    -webkit-touch-callout: none;
  }
  .ezuikit-player {
    min-height: 240px;
    height: 100%;
  }
  .loading-text { font-size: 12px; }
  .network-indicator { top: 6px; right: 6px; padding: 3px 8px; font-size: 9px; }
  .btn-retry { padding: 8px 20px; font-size: 13px; }
}

/* 小屏手机（< 400px） */
@media (max-width: 400px) {
  .ezuikit-player {
    min-height: 200px;
  }
}

/* 横屏模式 */
@media (max-height: 500px) and (orientation: landscape) {
  .video-player-wrapper {
    height: 100vh;
    border-radius: 0;
  }
  .ezuikit-player {
    min-height: 100%;
    height: 100vh;
  }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeSlideIn { to { opacity: 1; transform: translateY(0); } }
@keyframes pulse-text { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
@keyframes pulse-retry { 0%, 100% { opacity: 0.6; transform: scale(1); } 50% { opacity: 1; transform: scale(1.1); } }
</style>
