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
      <p class="loading-text">正在适配...</p>
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

    <!-- 播放器容器（EZUIKit pcLive 模板：全屏视频 + 右侧悬浮控件栏） -->
    <div id="ezuikitPlayerId" class="ezuikit-player" v-show="!error"></div>

    <!-- 网速指示器 -->
    <div v-if="initialized && !isSwitchingQuality && !isRetrying && !isSwitchingChannel" class="network-indicator" :class="networkQuality">
      <span class="ni-dot"></span>
      <span class="ni-label">{{ networkLabel }}</span>
    </div>

    <!-- ====== 频道切换导航按钮（pcLive 模板不提供此功能） ====== -->
    <div v-if="false" class="channel-nav-overlay">
      <button class="ch-nav-btn ch-nav-left" @click.stop="emitPrevChannel" aria-label="上一个通道">
        <svg viewBox="0 0 24 24" width="22" height="22"><polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
      <button class="ch-nav-btn ch-nav-right" @click.stop="emitNextChannel" aria-label="下一个通道">
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
  channel: { type: Object, default: null },
  cachedToken: { type: String, default: null },
  appKey: { type: String, default: '' }
})

const emit = defineEmits(['prev-channel', 'next-channel'])

// ====== 独立页面支持：无父组件传 channel 时从 sessionStorage 读取 ======
const localChannel = ref(null)

// 尝试从 sessionStorage 获取选中通道
try {
  const saved = sessionStorage.getItem('mobileSelectedChannel')
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed && parsed.deviceSerial && parsed.channelNo != null) {
      localChannel.value = parsed
    }
  }
} catch (e) {}

// 活跃通道：优先使用 props.channel（组件模式），否则用 localChannel（页面模式）
const activeChannel = computed(() => props.channel || localChannel.value)

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
// isMobile 初始检测，配合 resize 事件保持同步
const isMobile = ref(window.innerWidth <= 768 || 'ontouchstart' in window)
const ptzPanelOpen = ref(false)

// 缩放模式：0=拉伸填充, 1=等比缩放(含黑边), 2=裁剪填充
// 默认 0（拉伸填充）—— 移动端不用 cover(2)，避免放大裁剪导致像素级模糊
const defaultScaleMode = 0
const scaleMode = ref(parseInt(localStorage.getItem('scaleMode') || String(defaultScaleMode)))

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
let channelSwitchGen = 0  // 通道切换世代计数器，防止竞态
let qualityUpgraded = false  // 是否已执行过画质升级切换

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
  if (!activeChannel.value) return ''
  // 移动端强制 HD 画质
  const isMobileDevice = window.innerWidth <= 768 || 'ontouchstart' in window
  const quality = isMobileDevice ? 'hd' : (localStorage.getItem('videoQuality') || 'hd')
  // 萤石云地址格式：高清带 .hd，其他画质（流畅）不带后缀
  const qualitySuffix = quality === 'hd' ? '.hd' : ''
  const url = `ezopen://open.ys7.com/${activeChannel.value.deviceSerial}/${activeChannel.value.channelNo}${qualitySuffix}.live`
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

  // 统一使用 pcLive 模板（右侧控制面板），兼容桌面和移动端横屏。
  // mobileLive 模板在移动端竖屏模式下画面过小，清晰度严重受损。
  // pcLive 提供全屏视频 + 右侧悬浮控件，移动端配合横屏锁定效果最佳。
  const playerTemplate = 'mobileLive'
  // 官方推荐：宽撑满，高按 16:9 比例
  var width = document.documentElement.clientWidth;
  var height = document.documentElement.clientWidth * 9 / 16;
  playerInstance = new EZUIKit.EZUIKitPlayer({
    id: 'ezuikitPlayerId',
    accessToken: currentToken,
    url: playUrl.value,
    template: playerTemplate,          // pcLive 模板：全屏视频 + 右侧悬浮控件栏
    autoplay: true,
    staticPath: '/ezuikit_cdn',
    scaleMode: scaleMode.value,          // 从 localStorage 读取用户偏好的缩放模式
    audio: 0,
    width: width,
    height: height,
    // 从用户偏好读取画质。SDK 内部使用数字等级（0=流畅, 1=标清, 2=高清），
    // 传入字符串 'hd' 会导致 setVideoLevel 内部 parseInt('hd') = NaN。
    videoLevel: parseInt(localStorage.getItem('videoQuality') || '2', 10),
    // videoLevelList: [
    //   { value: 'hd', name: '高清' },
    //   { value: 'sd', name: '标清' },
    //   { value: 'fluent', name: '流畅' }
    // ],
    mobileExtendOptions: {
      controls: ['fullScreen', 'hdSwitch'],   // 移除原生 ptzControl，使用自建底部 PTZ 面板
      showClose: false,
      showBack: false,
      showPTZ: false,
    },
    handleSuccess: () => {
      // 第一次 handleSuccess：不展示模糊视频，直接触发重建
      if (!qualityUpgraded) {
        qualityUpgraded = true
        isSwitchingQuality.value = true
        loading.value = true
        if (playerInstance && typeof playerInstance.changePlayUrl === 'function') {
          playerInstance.changePlayUrl(playUrl.value)
        }
        return
      }
      // 第二次 handleSuccess：重建完成，canvas 已适配容器
      clearLoadTimeout()
      initialized.value = true; loading.value = false; retryCount.value = 0
      isRetrying.value = false; isSwitchingQuality.value = false; isSwitchingChannel.value = false; loadStep.value = 0
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

// ====== PTZ 云台控制 ======
function togglePtzPanel() {
  ptzPanelOpen.value = !ptzPanelOpen.value
}

function startPtz(direction) {
  if (playerInstance && typeof playerInstance.startPTZ === 'function') {
    try { playerInstance.startPTZ({ direction, speed: 1 }) }
    catch (e) { console.warn('PTZ start failed:', e) }
  }
}

function stopPtz() {
  if (playerInstance && typeof playerInstance.stopPTZ === 'function') {
    try { playerInstance.stopPTZ() }
    catch (e) { console.warn('PTZ stop failed:', e) }
  }
}

// ====== 缩放模式循环切换（0→1→2→0） ======
function cycleScaleMode() {
  scaleMode.value = (scaleMode.value + 1) % 3  // 0→1→2→0
  localStorage.setItem('scaleMode', String(scaleMode.value))
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

// ====== 响应式检测移动端 ======
function updateIsMobile() {
  isMobile.value = window.innerWidth <= 768 || 'ontouchstart' in window
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
watch(activeChannel, (newChannel, oldChannel) => {
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
  qualityUpgraded = false  // 新通道需要重新升画质

  // 优雅切换：先标记切换状态（显示过渡覆盖层），再重建播放器
  isSwitchingChannel.value = true
  // 关闭 PTZ 面板（切换通道时自动收起）
  ptzPanelOpen.value = false

  // 使用世代计数器防止竞态：如果多次快速切换，只有最新的一次生效
  const myGen = ++channelSwitchGen

  // 短暂延迟确保 DOM 更新覆盖层后再销毁播放器
  setTimeout(() => {
    if (myGen !== channelSwitchGen) return // 已过期，忽略
    destroyPlayerInstance()
    initPlayer()
  }, 50)
}, { immediate: false })

// ====== 生命周期 ======
onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)

  // 页面模式（无 props.channel）：确保 sessionStorage 中有通道数据
  if (!props.channel && !localChannel.value) {
    // 如果 activeChannel 不存在 -> 等待路由参数或显示错误
    error.value = '未选择播放通道'
    loading.value = false
    return
  }

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
  destroyPlayer()

  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  window.removeEventListener('orientationchange', checkOrientation)
  window.removeEventListener('resize', checkOrientation)
  window.removeEventListener('resize', updateIsMobile)
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

/* ====== 频道切换导航按钮（pcLive 模板不提供此功能） ====== */
.channel-nav-overlay {
  position: absolute; inset: 0; z-index: 30;
  pointer-events: none;              /* 触摸穿透 → EZUIKit 原生控件可操作 */
  touch-action: none;
}

.ch-nav-btn {
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
.ch-nav-btn:active {
  background: rgba(0, 0, 0, 0.5);
}
.ch-nav-left { left: 6px; }
.ch-nav-right { right: 6px; }

/* ====== 自定义 PTZ 触发按钮 ====== */
.mobile-ptz-trigger {
  position: absolute;
  bottom: 80px;
  right: 12px;
  z-index: 40;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  pointer-events: auto;
  transition: all 0.2s;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

.mobile-ptz-trigger:active {
  background: rgba(59, 130, 246, 0.5);
  border-color: rgba(59, 130, 246, 0.5);
  transform: scale(0.92);
}

/* ====== 自定义底部 PTZ 控制面板 ====== */
.custom-ptz-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
  background: rgba(15, 15, 26, 0.92);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px 16px 0 0;
  padding: 16px 20px calc(16px + env(safe-area-inset-bottom, 0));
  pointer-events: auto;
  touch-action: none;
}

.ptz-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.ptz-panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.3px;
}

.ptz-close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 50%;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.2s;
}

.ptz-close-btn:active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.ptz-panel-body {
  display: flex;
  justify-content: center;
}

.ptz-dpad {
  display: grid;
  grid-template-columns: 56px 56px 56px;
  grid-template-rows: 56px 56px 56px;
  gap: 4px;
  justify-items: center;
  align-items: center;
}

.ptz-dir-btn {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: all 0.15s;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  touch-action: none;
}

.ptz-dir-btn:active {
  background: rgba(59, 130, 246, 0.4);
  border-color: rgba(59, 130, 246, 0.5);
  transform: scale(0.92);
}

.ptz-up { grid-column: 2; grid-row: 1; }
.ptz-left { grid-column: 1; grid-row: 2; }
.ptz-center {
  grid-column: 2;
  grid-row: 2;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
}
.ptz-right { grid-column: 3; grid-row: 2; }
.ptz-down { grid-column: 2; grid-row: 3; }

/* PTZ 面板滑入/滑出动画 */
.ptz-slide-enter-active,
.ptz-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease;
}
.ptz-slide-enter-from {
  transform: translateY(100%);
  opacity: 0;
}
.ptz-slide-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

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

/* ====== pcLive 模板移动端布局覆盖（解决 iPhone 横屏视频被挤压问题） ====== */
@media (max-width: 768px) {
  /* 1. 播放器主容器 — 确保全宽 */
  .ezuikit-player :deep(.ezui-player-widget) {
    width: 100% !important;
    flex-direction: column !important;
  }

  /* 2. 右侧工具栏 — 浮动覆盖在视频右上角 */
  .ezuikit-player :deep(.ezui-player-right) {
    position: absolute !important;
    top: 0 !important;
    right: 0 !important;
    width: auto !important;
    height: auto !important;
    background: transparent !important;
    z-index: 50 !important;
    pointer-events: auto !important;
  }

  /* 3. 左侧视频区域 — 占满全宽 */
  .ezuikit-player :deep(.ezui-player-left) {
    width: 100% !important;
    height: 100% !important;
    position: relative !important;
  }

  /* 4. video/canvas 元素 — 覆盖填满 */
  .ezuikit-player :deep(.ezui-player-left video),
  .ezuikit-player :deep(.ezui-player-left canvas) {
    width: 100% !important;
    height: 100% !important;
    object-fit: cover !important;
  }

  /* 5. 工具栏按钮组 — 紧凑排列在右上角 */
  .ezuikit-player :deep(.ezui-player-right .ezui-toolbar) {
    flex-direction: column !important;
    gap: 2px !important;
    padding: 4px !important;
    background: rgba(0, 0, 0, 0.35) !important;
    backdrop-filter: blur(8px) !important;
    -webkit-backdrop-filter: blur(8px) !important;
    border-radius: 8px !important;
    margin: 8px !important;
  }

  /* 6. PTZ 弹出面板 — 调整层级避免被遮挡 */
  .ezuikit-player :deep(.ezui-ptz-panel),
  .ezuikit-player :deep(.ezui-popover) {
    z-index: 60 !important;
  }

  /* 7. 隐藏非必要的 pcLive 装饰元素 */
  .ezuikit-player :deep(.ezui-player-header) {
    display: none !important;
  }
  .ezuikit-player :deep(.ezui-player-footer) {
    display: none !important;
  }

  /* 8. 播放器外层容器 — 强制定位确保覆盖生效 */
  .ezuikit-player {
    position: relative !important;
    overflow: hidden !important;
  }

  /* 9. 小高度横屏优化（工具栏横向排列） */
  @media (max-height: 500px) and (orientation: landscape) {
    .ezuikit-player :deep(.ezui-player-right .ezui-toolbar) {
      flex-direction: row !important;
      padding: 3px 6px !important;
      margin: 4px !important;
      gap: 1px !important;
    }
    .ezuikit-player :deep(.ezui-player-right .ezui-toolbar button) {
      transform: scale(0.75) !important;
    }
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
