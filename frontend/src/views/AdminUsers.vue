<template>
  <div class="admin-users">
    <div class="page-header">
      <div>
        <h1>用户管理</h1>
        <p class="page-desc">管理系统用户 - 新增、编辑、启用/禁用用户</p>
      </div>
      <button class="btn-add" @click="showCreateDialog = true">
        <svg viewBox="0 0 24 24" width="18" height="18">
          <line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" stroke-width="2.5"/>
          <line x1="5" y1="12" x2="19" y2="12" stroke="currentColor" stroke-width="2.5"/>
        </svg>
        新增用户
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="btn-retry" @click="fetchUsers">重新加载</button>
    </div>

    <!-- 用户列表 -->
    <div v-else class="user-table-wrapper">
      <table class="user-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>显示名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>登录失败次数</th>
            <th>最后登录</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td class="cell-id">{{ user.id }}</td>
            <td class="cell-username">{{ user.username }}</td>
            <td>{{ user.displayName || '-' }}</td>
            <td class="cell-email">{{ user.email }}</td>
            <td>
              <span class="role-badge" :class="user.role === 'ROLE_ADMIN' ? 'admin' : 'user'">
                {{ user.role === 'ROLE_ADMIN' ? '管理员' : '普通用户' }}
              </span>
            </td>
            <td>
              <span class="status-badge" :class="user.enabled ? 'enabled' : 'disabled'">
                {{ user.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="cell-attempts">
              <span :class="{ 'danger': user.failedLoginAttempts >= 3 }">
                {{ user.failedLoginAttempts }}
              </span>
            </td>
            <td class="cell-time">{{ user.lastLoginAt ? formatTime(user.lastLoginAt) : '-' }}</td>
            <td class="cell-actions">
              <button class="action-btn" @click="resetPassword(user)" title="重置密码">
                <svg viewBox="0 0 24 24" width="16" height="16">
                  <rect x="3" y="11" width="18" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4" fill="none" stroke="currentColor" stroke-width="2"/>
                </svg>
              </button>
              <button
                class="action-btn"
                :class="user.enabled ? 'warning' : 'success'"
                @click="toggleEnabled(user)"
                :title="user.enabled ? '禁用' : '启用'"
              >
                <svg viewBox="0 0 24 24" width="16" height="16">
                  <g v-if="user.enabled">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" fill="none" stroke="currentColor" stroke-width="2"/>
                    <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/>
                  </g>
                  <g v-else>
                    <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" stroke-width="2"/>
                    <path d="M9 9a3 3 0 015 5" fill="none" stroke="currentColor" stroke-width="2"/>
                  </g>
                </svg>
              </button>
              <button class="action-btn danger" @click="confirmDelete(user)" title="删除">
                <svg viewBox="0 0 24 24" width="16" height="16">
                  <polyline points="3 6 5 6 21 6" fill="none" stroke="currentColor" stroke-width="2"/>
                  <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" fill="none" stroke="currentColor" stroke-width="2"/>
                </svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 创建用户弹窗 -->
    <transition name="fade">
      <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
        <div class="modal-content form-modal">
          <div class="modal-header">
            <h2>新增用户</h2>
            <button class="btn-close" @click="showCreateDialog = false">
              <svg viewBox="0 0 24 24" width="24" height="24">
                <line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" stroke-width="2"/>
                <line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" stroke-width="2"/>
              </svg>
            </button>
          </div>

          <form class="user-form" @submit.prevent="createUser">
            <div class="form-group">
              <label>用户名 <span class="required">*</span></label>
              <input v-model="createForm.username" placeholder="3-50个字符，字母/数字/下划线" required maxlength="50"/>
              <small>只能包含字母、数字和下划线</small>
            </div>
            <div class="form-group">
              <label>邮箱 <span class="required">*</span></label>
              <input v-model="createForm.email" type="email" placeholder="user@example.com" required maxlength="100"/>
            </div>
            <div class="form-group">
              <label>密码 <span class="required">*</span></label>
              <input v-model="createForm.password" type="password" placeholder="至少8位，需包含字母和数字" required maxlength="100"/>
              <small>至少8位，需包含字母和数字</small>
            </div>
            <div class="form-group">
              <label>显示名称</label>
              <input v-model="createForm.displayName" placeholder="用户显示名称" maxlength="100"/>
            </div>
            <div class="form-group">
              <label>角色 <span class="required">*</span></label>
              <select v-model="createForm.role" required>
                <option value="ROLE_USER">普通用户</option>
                <option value="ROLE_ADMIN">管理员</option>
              </select>
            </div>
            <div class="form-actions">
              <button type="button" class="btn-cancel" @click="showCreateDialog = false">取消</button>
              <button type="submit" class="btn-submit" :disabled="creating">
                {{ creating ? '创建中...' : '创建用户' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </transition>

    <!-- 重置密码弹窗 -->
    <transition name="fade">
      <div v-if="resetPwdUser" class="modal-overlay" @click.self="resetPwdUser = null">
        <div class="modal-content form-modal">
          <div class="modal-header">
            <h2>重置密码 - {{ resetPwdUser.username }}</h2>
            <button class="btn-close" @click="resetPwdUser = null">
              <svg viewBox="0 0 24 24" width="24" height="24">
                <line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" stroke-width="2"/>
                <line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" stroke-width="2"/>
              </svg>
            </button>
          </div>

          <form class="user-form" @submit.prevent="handleResetPassword">
            <div class="form-group">
              <label>新密码 <span class="required">*</span></label>
              <input v-model="newPassword" type="password" placeholder="至少8位，需包含字母和数字" required minlength="8" maxlength="100"/>
              <small>至少8位，需包含字母和数字</small>
            </div>
            <div class="form-actions">
              <button type="button" class="btn-cancel" @click="resetPwdUser = null">取消</button>
              <button type="submit" class="btn-submit" :disabled="resetting">
                {{ resetting ? '重置中...' : '确认重置' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'

const users = ref([])
const loading = ref(true)
const error = ref('')
const showCreateDialog = ref(false)
const creating = ref(false)
const resetPwdUser = ref(null)
const newPassword = ref('')
const resetting = ref(false)

const createForm = reactive({
  username: '',
  email: '',
  password: '',
  displayName: '',
  role: 'ROLE_USER'
})

onMounted(() => {
  fetchUsers()
})

async function fetchUsers() {
  loading.value = true
  error.value = ''
  try {
    const response = await adminApi.getUsers()
    users.value = response.data.data || []
  } catch (err) {
    error.value = err.friendlyMessage || '获取用户列表失败'
  } finally {
    loading.value = false
  }
}

async function createUser() {
  creating.value = true
  try {
    await adminApi.createUser({ ...createForm })
    showCreateDialog.value = false
    createForm.username = ''
    createForm.email = ''
    createForm.password = ''
    createForm.displayName = ''
    createForm.role = 'ROLE_USER'
    await fetchUsers()
  } catch (err) {
    alert('创建用户失败: ' + (err.friendlyMessage || '请重试'))
  } finally {
    creating.value = false
  }
}

async function toggleEnabled(user) {
  const action = user.enabled ? '禁用' : '启用'
  if (!confirm(`确定要${action}用户 "${user.username}" 吗？`)) return
  try {
    await adminApi.toggleEnabled(user.id)
    await fetchUsers()
  } catch (err) {
    alert(`${action}用户失败: ` + (err.friendlyMessage || '请重试'))
  }
}

function resetPassword(user) {
  resetPwdUser.value = user
  newPassword.value = ''
}

async function handleResetPassword() {
  if (!newPassword.value || newPassword.value.length < 8) return
  resetting.value = true
  try {
    await adminApi.resetPassword(resetPwdUser.value.id, newPassword.value)
    resetPwdUser.value = null
    alert('密码重置成功')
  } catch (err) {
    alert('重置密码失败: ' + (err.friendlyMessage || '请重试'))
  } finally {
    resetting.value = false
  }
}

async function confirmDelete(user) {
  if (user.username === 'admin') {
    alert('不能删除默认管理员账户')
    return
  }
  if (!confirm(`确定要删除用户 "${user.username}" 吗？此操作不可恢复。`)) return
  try {
    await adminApi.deleteUser(user.id)
    await fetchUsers()
  } catch (err) {
    alert('删除用户失败: ' + (err.friendlyMessage || '请重试'))
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}
</script>

<style scoped>
.admin-users {
  max-width: 1400px;
  margin: 0 auto;
  animation: fadeIn 0.3s ease;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 700;
}

.page-desc {
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 4px;
}

.btn-add {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-add:hover {
  background: var(--primary-dark);
  transform: translateY(-1px);
}

.loading-state,
.error-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  margin: 0 auto 16px;
}

.btn-retry {
  margin-top: 12px;
  padding: 8px 20px;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.user-table-wrapper {
  background: var(--bg-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow-x: auto;
  border: 1px solid var(--border);
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.user-table th {
  background: #f8fafc;
  text-align: left;
  padding: 12px 16px;
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  color: var(--text-secondary);
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
}

.user-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
}

.user-table tbody tr:hover {
  background: #f8fafc;
}

.cell-id {
  font-family: 'SF Mono', 'Consolas', monospace;
  color: var(--text-secondary);
  font-size: 12px;
}

.cell-username {
  font-weight: 600;
}

.cell-email {
  font-size: 13px;
  color: var(--text-secondary);
}

.cell-time {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.cell-attempts .danger {
  color: var(--danger);
  font-weight: 600;
}

.role-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 600;
}

.role-badge.admin {
  background: #e8f5e9;
  color: #2e7d32;
}

.role-badge.user {
  background: #e3f2fd;
  color: #1565c0;
}

.status-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 600;
}

.status-badge.enabled {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-badge.disabled {
  background: #fce4ec;
  color: #c62828;
}

.cell-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  padding: 6px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: 4px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f1f5f9;
  color: var(--text);
}

.action-btn.warning:hover {
  background: #fff3e0;
  color: var(--warning);
}

.action-btn.success:hover {
  background: #e8f5e9;
  color: var(--success);
}

.action-btn.danger:hover {
  background: #fff0f0;
  color: var(--danger);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  max-height: 90vh;
  overflow-y: auto;
}

.form-modal {
  width: 90vw;
  max-width: 480px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.modal-header h2 {
  font-size: 18px;
  font-weight: 600;
}

.btn-close {
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: 4px;
  transition: all 0.2s;
}

.btn-close:hover {
  background: #f1f5f9;
  color: var(--text);
}

.user-form {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 14px;
  font-weight: 500;
}

.required {
  color: var(--danger);
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 10px 12px;
  border: 2px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group select:focus {
  border-color: var(--primary);
}

.form-group small {
  font-size: 12px;
  color: var(--text-secondary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.btn-cancel,
.btn-submit {
  padding: 10px 24px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-secondary);
}

.btn-cancel:hover {
  background: #f1f5f9;
}

.btn-submit {
  background: var(--primary);
  border: none;
  color: #fff;
}

.btn-submit:hover {
  background: var(--primary-dark);
}

.btn-submit:disabled {
  opacity: 0.6;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .admin-page { padding: 16px; }

  .admin-header { flex-direction: column; gap: 12px; align-items: flex-start; }

  .user-table { font-size: 12px; }
  .user-table th, .user-table td { padding: 8px 8px; }
  .user-table td:nth-child(6), .user-table th:nth-child(6) { display: none; } /* 隐藏最后登录时间 */
  .user-table td:nth-child(7), .user-table th:nth-child(7) { display: none; } /* 隐藏失败次数 */

  .action-btn { font-size: 11px; padding: 4px 8px; }
  .table-wrap { margin: 0 -16px; border-radius: 0; }

  /* 弹窗全屏 */
  .modal-overlay { align-items: flex-end; }
  .modal-content {
    max-width: 100%;
    border-radius: 16px 16px 0 0;
    margin-top: auto;
    max-height: 80vh;
  }
}
</style>
