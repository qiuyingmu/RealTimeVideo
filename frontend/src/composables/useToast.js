/**
 * Toast 通知 + 确认弹窗（基于 SweetAlert2）
 *
 * 用法：
 *   import { toast, confirm } from '@/composables/useToast'
 *   toast.success('操作成功')
 *   toast.error('操作失败')
 *   const ok = await confirm('确定要删除吗？')
 */
import Swal from 'sweetalert2'

// ====== 全局默认样式 ======
const Toast = Swal.mixin({
  toast: true,
  position: window.innerWidth <= 768 ? 'top' : 'top-end',
  showConfirmButton: false,
  timer: 3000,
  timerProgressBar: true,
  didOpen: (toast) => {
    toast.addEventListener('mouseenter', Swal.stopTimer)
    toast.addEventListener('mouseleave', Swal.resumeTimer)
  },
  customClass: {
    popup: 'swal-toast-popup',
    title: 'swal-toast-title',
    timerProgressBar: 'swal-toast-progress'
  }
})

// 图标颜色映射
const ICON_COLORS = {
  success: '#10b981',
  error: '#ef4444',
  warning: '#f59e0b',
  info: '#3b82f6'
}

export const toast = {
  success: (msg) => Toast.fire({ icon: 'success', title: msg, iconColor: ICON_COLORS.success }),
  error:   (msg) => Toast.fire({ icon: 'error',   title: msg, iconColor: ICON_COLORS.error }),
  warning: (msg) => Toast.fire({ icon: 'warning', title: msg, iconColor: ICON_COLORS.warning }),
  info:    (msg) => Toast.fire({ icon: 'info',    title: msg, iconColor: ICON_COLORS.info }),
}

/**
 * 确认弹窗
 * @param {string} message 提示内容
 * @param {string} title   标题（可选）
 * @returns {Promise<boolean>}
 */
export async function confirm(message, title = '确认操作') {
  const isMobile = window.innerWidth <= 768

  const result = await Swal.fire({
    title,
    text: message,
    icon: 'question',
    iconColor: '#f59e0b',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    confirmButtonColor: '#3b82f6',
    cancelButtonColor: '#64748b',
    reverseButtons: true,
    buttonsStyling: true,
    focusConfirm: false,
    customClass: {
      popup: 'swal-confirm-popup',
      title: 'swal-confirm-title',
      confirmButton: 'swal-btn-confirm',
      cancelButton: 'swal-btn-cancel',
      icon: 'swal-confirm-icon'
    },
    padding: isMobile ? '20px 16px' : '24px 32px',
    width: isMobile ? '90%' : '400px'
  })

  return result.isConfirmed
}

// ====== 注入全局样式 ======
const style = document.createElement('style')
style.textContent = `
/* Toast 弹窗 */
.swal-toast-popup {
  border-radius: 10px !important;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15) !important;
  padding: 10px 18px !important;
}
.swal-toast-title {
  font-size: 14px !important;
  font-weight: 600 !important;
}
.swal-toast-progress {
  height: 2px !important;
  background: #e2e8f0 !important;
}

/* 确认弹窗 */
.swal-confirm-popup {
  border-radius: 14px !important;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2) !important;
}
.swal-confirm-title {
  font-size: 17px !important;
  font-weight: 700 !important;
  color: #1e293b !important;
}
.swal-confirm-icon {
  width: 44px !important;
  height: 44px !important;
}
.swal-btn-confirm,
.swal-btn-cancel {
  border-radius: 8px !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  padding: 10px 24px !important;
  transition: all 0.2s !important;
}
.swal-btn-confirm:hover {
  transform: translateY(-1px) !important;
  box-shadow: 0 4px 12px rgba(59,130,246,0.3) !important;
}
.swal-btn-cancel:hover {
  transform: translateY(-1px) !important;
}

/* 深色模式 */
html.dark .swal-toast-popup {
  background: #1e293b !important;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4) !important;
}
html.dark .swal-toast-title {
  color: #e2e8f0 !important;
}
html.dark .swal-toast-progress {
  background: #334155 !important;
}
html.dark .swal-confirm-popup {
  background: #1e293b !important;
}
html.dark .swal-confirm-title {
  color: #e2e8f0 !important;
}
html.dark .swal-btn-cancel {
  color: #94a3b8 !important;
}
html.dark .swal-confirm-popup .swal2-html-container {
  color: #94a3b8 !important;
}
`
document.head.appendChild(style)
