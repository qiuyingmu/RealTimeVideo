<template>
  <div>
    <!-- Toast 通知容器 -->
    <div class="toast-container">
      <transition-group name="toast-slide" tag="div">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="toast-item"
          :class="'toast-' + t.type"
        >
          <span class="toast-icon">
            <svg v-if="t.type === 'success'" viewBox="0 0 24 24" width="18" height="18">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <polyline points="22 4 12 14.01 9 11.01" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            <svg v-else-if="t.type === 'error'" viewBox="0 0 24 24" width="18" height="18">
              <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
              <line x1="15" y1="9" x2="9" y2="15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <line x1="9" y1="9" x2="15" y2="15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            <svg v-else-if="t.type === 'warning'" viewBox="0 0 24 24" width="18" height="18">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" fill="none" stroke="currentColor" stroke-width="2"/>
              <line x1="12" y1="9" x2="12" y2="13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18">
              <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
              <line x1="12" y1="16" x2="12" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <line x1="12" y1="8" x2="12.01" y2="8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </span>
          <span class="toast-message">{{ t.message }}</span>
        </div>
      </transition-group>
    </div>

    <!-- 确认弹窗 -->
    <Teleport to="body">
      <transition name="confirm-fade">
        <div v-if="confirmState.visible" class="confirm-overlay" @click.self="resolveConfirm(false)">
          <div class="confirm-dialog">
            <div class="confirm-header">
              <svg viewBox="0 0 24 24" width="22" height="22" class="confirm-icon">
                <circle cx="12" cy="12" r="10" fill="none" stroke="#f59e0b" stroke-width="2"/>
                <line x1="12" y1="8" x2="12" y2="13" stroke="#f59e0b" stroke-width="2" stroke-linecap="round"/>
                <circle cx="12" cy="16" r="1" fill="#f59e0b"/>
              </svg>
              <span class="confirm-title">{{ confirmState.title }}</span>
            </div>
            <p class="confirm-message">{{ confirmState.message }}</p>
            <div class="confirm-actions">
              <button class="confirm-btn cancel" @click="resolveConfirm(false)">取消</button>
              <button class="confirm-btn ok" @click="resolveConfirm(true)">确定</button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { useToasts, useConfirm } from '@/composables/useToast'

const { toasts } = useToasts()
const { confirmState, resolveConfirm } = useConfirm()
</script>

<style scoped>
/* ====== Toast 容器 ====== */
.toast-container {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
  pointer-events: auto;
  white-space: nowrap;
}

.toast-success {
  background: #065f46;
  color: #d1fae5;
  border: 1px solid rgba(52, 211, 153, 0.3);
}

.toast-error {
  background: #7f1d1d;
  color: #fecaca;
  border: 1px solid rgba(248, 113, 113, 0.3);
}

.toast-warning {
  background: #78350f;
  color: #fed7aa;
  border: 1px solid rgba(251, 191, 36, 0.3);
}

.toast-info {
  background: #1e3a5f;
  color: #bfdbfe;
  border: 1px solid rgba(96, 165, 250, 0.3);
}

.toast-icon {
  display: flex;
  flex-shrink: 0;
}

/* ====== Toast 动画 ====== */
.toast-slide-enter-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.toast-slide-leave-active {
  transition: all 0.25s ease-in;
}
.toast-slide-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.95);
}
.toast-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}

/* ====== 确认弹窗 ====== */
.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

.confirm-dialog {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 16px;
  padding: 28px 32px 24px;
  min-width: 320px;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.confirm-title {
  font-size: 17px;
  font-weight: 700;
  color: #e2e8f0;
}

.confirm-message {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.6;
  margin-bottom: 24px;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.confirm-btn.cancel {
  background: rgba(255, 255, 255, 0.08);
  color: #94a3b8;
}

.confirm-btn.cancel:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #e2e8f0;
}

.confirm-btn.ok {
  background: #3b82f6;
  color: #fff;
}

.confirm-btn.ok:hover {
  background: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.confirm-btn.ok:active {
  transform: translateY(0);
}

/* ====== 确认弹窗动画 ====== */
.confirm-fade-enter-active {
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}
.confirm-fade-leave-active {
  transition: all 0.15s ease-in;
}
.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}
.confirm-fade-enter-from .confirm-dialog,
.confirm-fade-leave-to .confirm-dialog {
  transform: scale(0.95);
}
</style>
