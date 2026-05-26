<template>
  <div class="logs-page">
    <div class="page-header">
      <h1>操作日志</h1>
      <p class="page-desc">记录用户的关键操作行为，用于安全审计和追溯</p>
    </div>

    <!-- 筛选栏 -->
    <div class="filters">
      <select v-model="filterAction" @change="loadLogs(1)">
        <option value="">全部操作类型</option>
        <option value="LOGIN">登录成功</option>
        <option value="LOGIN_FAILED">登录失败</option>
        <option value="LOGOUT">登出</option>
        <option value="PTZ_CONTROL">云台控制</option>
        <option value="SYNC_DEVICES">同步设备</option>
        <option value="CREATE_USER">创建用户</option>
        <option value="USER_TOGGLE">启用/禁用</option>
        <option value="RESET_PASSWORD">重置密码</option>
        <option value="DELETE_USER">删除用户</option>
        <option value="UPDATE_PERMISSION">更新权限</option>
        <option value="REMOVE_PERMISSION">移除权限</option>
      </select>
      <input v-model="filterUsername" placeholder="用户名" @input="debounceLoad" />
      <span class="log-count">共 {{ totalLogs }} 条记录</span>
    </div>

    <!-- 日志列表 -->
    <div class="log-list">
      <div v-if="loading" class="loading-state">
        <div class="mini-spinner"></div>
      </div>

      <div v-else-if="logs.length === 0" class="empty-state">
        <p>暂无操作日志</p>
      </div>

      <table v-else class="log-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>用户</th>
            <th>操作类型</th>
            <th>目标</th>
            <th class="col-detail">详情</th>
            <th>IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="cell-time">{{ formatTime(log.createdAt) }}</td>
            <td>{{ log.username || '-' }}</td>
            <td>
              <span class="action-badge" :class="log.action">
                {{ actionLabel(log.action) }}
              </span>
            </td>
            <td class="cell-target">{{ log.target || '-' }}</td>
            <td class="cell-detail">{{ log.details || '-' }}</td>
            <td class="cell-ip">{{ log.ipAddress || '-' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="currentPage <= 1" @click="loadLogs(currentPage - 1)">
          上一页
        </button>
        <span>{{ currentPage }} / {{ totalPages }}</span>
        <button :disabled="currentPage >= totalPages" @click="loadLogs(currentPage + 1)">
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api'

const logs = ref([])
const loading = ref(true)
const currentPage = ref(1)
const totalPages = ref(1)
const totalLogs = ref(0)
const filterAction = ref('')
const filterUsername = ref('')
let debounceTimer = null

function debounceLoad() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => loadLogs(1), 300)
}

async function loadLogs(page) {
  loading.value = true
  currentPage.value = page
  try {
    const params = { page: page - 1, size: 30 }
    if (filterAction.value) params.action = filterAction.value
    if (filterUsername.value) params.username = filterUsername.value
    const res = await adminApi.getLogs(params)
    const data = res.data.data
    logs.value = data.content || []
    totalPages.value = data.totalPages || 1
    totalLogs.value = data.totalElements || 0
  } catch (e) {
    console.error('加载日志失败', e)
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

function actionLabel(action) {
  const map = {
    LOGIN: '登录成功', LOGIN_FAILED: '登录失败', LOGOUT: '登出',
    PTZ_CONTROL: '云台控制', SYNC_DEVICES: '同步设备',
    CREATE_USER: '创建用户', USER_TOGGLE: '启用/禁用',
    RESET_PASSWORD: '重置密码', DELETE_USER: '删除用户',
    UPDATE_PERMISSION: '更新权限', REMOVE_PERMISSION: '移除权限'
  }
  return map[action] || action
}

onMounted(() => loadLogs(1))
</script>

<style scoped>
.logs-page {
  padding: 24px;
}

.page-header h1 { font-size: 22px; font-weight: 700; margin: 0; }
.page-desc { color: var(--text-secondary); font-size: 13px; margin: 4px 0 0; }

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  flex-wrap: wrap;
}

.filters select, .filters input {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  outline: none;
  background: #fff;
}

.filters select:focus, .filters input:focus {
  border-color: var(--primary);
}

.log-count {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}

.log-list {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  overflow: hidden;
}

.loading-state {
  padding: 40px;
  display: flex;
  justify-content: center;
}

.mini-spinner {
  width: 24px; height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary);
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.log-table th {
  text-align: left;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}

.log-table td {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

.log-table tr:last-child td { border-bottom: none; }
.log-table tr:hover td { background: #f8fafc; }

.cell-time { white-space: nowrap; font-family: monospace; font-size: 12px; }
.cell-target { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cell-detail { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary); }
.cell-ip { font-family: monospace; font-size: 12px; color: var(--text-secondary); }

.action-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.action-badge.LOGIN { background: #dcfce7; color: #166534; }
.action-badge.LOGIN_FAILED { background: #fef2f2; color: #991b1b; }
.action-badge.LOGOUT { background: #f1f5f9; color: #475569; }
.action-badge.PTZ_CONTROL { background: #eff6ff; color: #1e40af; }
.action-badge.SYNC_DEVICES { background: #fef3c7; color: #92400e; }
.action-badge.CREATE_USER { background: #f0fdf4; color: #166534; }
.action-badge.USER_TOGGLE { background: #fdf4ff; color: #86198f; }
.action-badge.RESET_PASSWORD { background: #fff7ed; color: #9a3412; }
.action-badge.DELETE_USER { background: #fef2f2; color: #991b1b; }
.action-badge.UPDATE_PERMISSION { background: #ecfeff; color: #155e75; }
.action-badge.REMOVE_PERMISSION { background: #fef2f2; color: #991b1b; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px;
  border-top: 1px solid var(--border);
}

.pagination button {
  padding: 6px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.pagination button:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }

/* 移动端 */
@media (max-width: 768px) {
  .logs-page { padding: 16px; }
  .filters { flex-direction: column; align-items: stretch; }
  .log-count { margin-left: 0; }
  .log-table { font-size: 12px; }
  .log-table th, .log-table td { padding: 8px 10px; }
  .cell-detail { display: none; }
  .col-detail { display: none; }
}
</style>
