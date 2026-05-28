/**
 * Toast 通知管理
 *
 * 提供全局 toast 通知和确认弹窗能力，
 * 替代原生 alert() / confirm() 调用。
 *
 * 用法：
 *   import { toast, confirm } from '@/composables/useToast'
 *   toast.success('操作成功')
 *   toast.error('操作失败')
 *   const ok = await confirm('确定要删除吗？')
 */
import { reactive, ref } from 'vue'

// ====== Toast 通知 ======

const TOAST_DURATION = 3000
const TOAST_TYPES = ['success', 'error', 'warning', 'info']

const toasts = reactive([])
let toastId = 0

function addToast(message, type = 'info', duration = TOAST_DURATION) {
  const id = ++toastId
  toasts.push({ id, message, type })
  setTimeout(() => {
    const idx = toasts.findIndex(t => t.id === id)
    if (idx !== -1) toasts.splice(idx, 1)
  }, duration)
}

export const toast = {
  success: (msg) => addToast(msg, 'success'),
  error:   (msg) => addToast(msg, 'error'),
  warning: (msg) => addToast(msg, 'warning'),
  info:    (msg) => addToast(msg, 'info'),
}

export function useToasts() {
  return { toasts }
}

// ====== 确认弹窗 ======

let confirmResolve = null
const confirmState = reactive({
  visible: false,
  message: '',
  title: '',
})

export async function confirm(message, title = '确认操作') {
  confirmState.title = title
  confirmState.message = message
  confirmState.visible = true
  return new Promise((resolve) => {
    confirmResolve = resolve
  })
}

export function useConfirm() {
  function resolveConfirm(ok) {
    confirmState.visible = false
    if (confirmResolve) {
      confirmResolve(ok)
      confirmResolve = null
    }
  }
  return { confirmState, resolveConfirm }
}
