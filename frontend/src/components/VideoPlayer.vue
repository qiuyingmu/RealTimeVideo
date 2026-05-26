<template>
  <div class="video-player-wrapper">
    <!-- 加载中 -->
    <div v-if="!initialized && !error && !isRetrying && !isSwitchingQuality" class="player-loading">
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

    <!-- 播放器容器（EZUIKit security模板内置全部控件） -->
    <div id="ezuikit-player" class="ezuikit-player" v-show="!error"></div>

    <!-- 网速指示器（唯一自定义叠加层） -->
    <div v-if="initialized && !isSwitchingQuality && !isRetrying" class="network-indicator" :class="networkQuality">
      <span class="ni-dot"></span>
      <span class="ni-label">{{ networkLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ezvizApi } from '@/api'
import EZUIKit from 'ezuikit-js'

const props = defineProps({
  channel: { type: Object, required: true },
  cachedToken: { type: String, default: null },
  appKey: { type: String, default: '' }
})

const MAX_RETRIES = 3

const loading = ref(true)
const initialized = ref(false)
const error = ref('')
const loadStep = ref(0)
const loadTimeout = ref(null)
const retryCount = ref(0)
const isRetrying = ref(false)
const isSwitchingQuality = ref(false)
const networkQuality = ref('good')

let playerInstance = null
let currentToken = null
let networkMonitor = null
let isInitializing = false

const CONNECT_TIMEOUT = 30000
const RETRY_BASE_DELAY = 1000
const RETRY_MAX_DELAY = 8000

const networkLabel = computed(() => ({
  good: '网络良好', fair: '网络一般', poor: '网络较差'
}[networkQuality.value] || '网络良好'))

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

const playUrl = computed(() => {
  // 始终使用 deviceSerial + channelNo 构建播放地址
  // NVR通道：ezopen://open.ys7.com/{NVR序列号}/{通道号}.hd.live
  // IPC直连：ezopen://open.ys7.com/{设备序列号}/1.hd.live
  // 注意：ipcSerial 仅用于内部标识，萤石云API不识别其作为直播地址
  const url = `ezopen://open.ys7.com/${props.channel.deviceSerial}/${props.channel.channelNo}.hd.live`
  // 仅开发环境打印调试日志
  if (import.meta.env.DEV) {
    console.log('[VideoPlayer] URL:',
      `ezopen://open.ys7.com/${props.channel.deviceSerial}/${props.channel.channelNo}.hd.live`,
      '| deviceSerial:', props.channel.deviceSerial,
      '| chNo:', props.channel.channelNo)
  }
  return url
})

const loadMessage = computed(() => {
  const msgs = { 0: '准备中...', 1: '获取凭证...', 2: '连接服务器...', 3: '加载视频流...' }
  return msgs[loadStep.value] || '连接中...'
})

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
    loading.value = false
  }
}

function destroyPlayerInstance() {
  if (playerInstance) {
    try { playerInstance.stop(); playerInstance.destroy() } catch (e) { /* ignore */ }
    playerInstance = null
  }
}

/** 创建播放器 — 基于萤石云官方 security 模板，内置全部控件 */
function createPlayer() {
  if (!currentToken) { error.value = '无法获取播放凭证，请刷新页面后重试'; loading.value = false; return }

  loadStep.value = 3

  const plugins = props.channel.talkSupported ? ['talk'] : []

  playerInstance = new EZUIKit.EZUIKitPlayer({
    id: 'ezuikit-player',
    accessToken: currentToken,
    url: playUrl.value,
    template: 'pcLive',             // PC直播模板（包含云台控制、截图、录制等）
    autoplay: true,                 // 自动播放
    staticPath: '/ezuikit_cdn',      // 通过Vite代理CDN并修正WASM的MIME类型
    scaleMode: 1,                   // 缩放模式: 0=contain(黑边) 1=cover(填充) 2=stretch
    audio: 0,                       // 默认静音（浏览器自动播放策略）
    width: '100%',
    height: '100%',
    handleSuccess: () => {
      clearLoadTimeout()
      initialized.value = true; loading.value = false; retryCount.value = 0
      isRetrying.value = false; isSwitchingQuality.value = false; loadStep.value = 0
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
        loading.value = false; isSwitchingQuality.value = false
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
    loading.value = false; isSwitchingQuality.value = false
  } finally {
    isInitializing = false
  }
}

function destroyPlayer() {
  destroyPlayerInstance()
}

onMounted(() => {
  initPlayer()
  networkMonitor = setInterval(checkNetworkQuality, 30000)
})

onUnmounted(() => {
  if (networkMonitor) { clearInterval(networkMonitor); networkMonitor = null }
  destroyPlayer()
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

/* ====== 加载 / 错误 / 重试 覆盖层 ====== */
.player-loading, .player-error {
  position: absolute; inset: 0; z-index: 10;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px; background: #0f0f1a; color: #94a3b8;
}
.quality-switching-overlay {
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

/* ====== 移动端适配 ====== */
@media (max-width: 768px) {
  .video-player-wrapper { border-radius: 0; }
  .ezuikit-player { min-height: 200px; }
  .loading-text { font-size: 12px; }
  .network-indicator { top: 6px; right: 6px; padding: 3px 8px; font-size: 9px; }
  .btn-retry { padding: 8px 20px; font-size: 13px; }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeSlideIn { to { opacity: 1; transform: translateY(0); } }
@keyframes pulse-text { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
@keyframes pulse-retry { 0%, 100% { opacity: 0.6; transform: scale(1); } 50% { opacity: 1; transform: scale(1.1); } }
</style>
