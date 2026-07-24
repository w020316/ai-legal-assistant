<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAuditLogs, type AuditLogVO, type AuditLogQuery } from '@/api'

const loading = ref(false)
const logs = ref<AuditLogVO[]>([])
const total = ref(0)
const query = ref<AuditLogQuery>({
  page: 1,
  size: 20,
  action: '',
})

// 操作类型选项
const actionOptions = [
  { label: '全部', value: '' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '注册', value: 'REGISTER' },
]

// 操作类型 -> 标签类型
function actionTagType(action: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (action) {
    case 'LOGIN':
      return 'success'
    case 'LOGOUT':
      return 'info'
    case 'REGISTER':
      return 'warning'
    default:
      return 'info'
  }
}

// 操作类型 -> 文案
function actionLabel(action: string): string {
  switch (action) {
    case 'LOGIN':
      return '登录'
    case 'LOGOUT':
      return '登出'
    case 'REGISTER':
      return '注册'
    default:
      return action
  }
}

// 时间戳格式化：ISO 时间戳转为本地时间 yyyy-MM-dd HH:mm:ss CST
function formatTimestamp(timestamp: string): string {
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} CST`
}

// 解析 detail JSON
function parseDetail(detail: string | null): string {
  if (!detail) return '—'
  try {
    const obj = JSON.parse(detail)
    return Object.entries(obj)
      .map(([k, v]) => `${k}: ${v}`)
      .join('  ·  ')
  } catch {
    return detail
  }
}

// 分页变化
function handlePageChange(page: number) {
  query.value.page = page
  fetchLogs()
}

// 每页条数变化
function handleSizeChange(size: number) {
  query.value.size = size
  query.value.page = 1
  fetchLogs()
}

// 筛选变化
function handleFilterChange() {
  query.value.page = 1
  fetchLogs()
}

// 拉取审计日志
async function fetchLogs() {
  loading.value = true
  try {
    const params: AuditLogQuery = {
      page: query.value.page,
      size: query.value.size,
    }
    if (query.value.action) {
      params.action = query.value.action
    }
    const res = await listAuditLogs(params)
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(fetchLogs)
</script>

<template>
  <div class="audit-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title" data-eyebrow="AUDIT · 审计日志">审计日志</h1>
      <el-button :loading="loading" size="small" @click="fetchLogs">刷新</el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">操作类型</span>
        <el-select
          v-model="query.action"
          placeholder="全部"
          size="small"
          style="width: 140px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="opt in actionOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
      <div class="filter-stat">
        <span class="stat-label">共</span>
        <span class="stat-val">{{ total }}</span>
        <span class="stat-label">条记录</span>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="table-card">
      <el-table
        v-loading="loading"
        :data="logs"
        stripe
        style="width: 100%"
        empty-text="暂无审计日志"
      >
        <el-table-column label="时间" width="200">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTimestamp(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small" effect="plain">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户 ID" width="100" prop="userId" />
        <el-table-column label="来源 IP" width="140">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="详情" min-width="200">
          <template #default="{ row }">
            <span class="cell-detail">{{ parseDetail(row.detail) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.audit-view {
  max-width: 1080px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 页面标题 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 0;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-primary);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.filter-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}
.filter-stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 13px;
  .stat-label {
    color: var(--color-text-secondary);
  }
  .stat-val {
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    font-weight: 600;
    color: var(--color-accent);
  }
}

/* 表格卡片 */
.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-top: 2px solid var(--color-accent);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

/* 单元格样式 */
.cell-time {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: var(--color-text-regular);
}
.cell-mono {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: var(--color-text-regular);
}
.cell-detail {
  font-size: 13px;
  color: var(--color-text-regular);
}

/* 分页栏 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .pagination-bar {
    justify-content: center;
    :deep(.el-pagination) {
      flex-wrap: wrap;
      justify-content: center;
    }
  }
}
</style>
