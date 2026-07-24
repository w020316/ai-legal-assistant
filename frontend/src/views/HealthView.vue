<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const appVersion = import.meta.env.VITE_APP_VERSION || 'v1.0.0'
const loading = ref(false)
const healthInfo = ref<{ status: string; service: string; timestamp: string } | null>(null)

// 时间戳格式化：ISO 时间戳转为本地时间 yyyy-MM-dd HH:mm:ss CST
function formatTimestamp(timestamp: string): string {
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} CST`
}

// 状态 -> 徽标样式类
function statusClass(status: string): string {
  const s = status.toLowerCase()
  if (s.includes('up') || s.includes('ok') || s.includes('healthy') || s.includes('success')) return 'is-ok'
  if (s.includes('down') || s.includes('fail') || s.includes('error')) return 'is-err'
  return 'is-warn'
}

// 状态文案
function statusLabel(status: string): string {
  const s = status.toLowerCase()
  if (s.includes('up') || s.includes('ok') || s.includes('healthy')) return '运行正常'
  if (s.includes('down') || s.includes('fail') || s.includes('error')) return '服务异常'
  if (s.includes('warn')) return '存在告警'
  return status
}

async function fetchHealth() {
  loading.value = true
  try {
    const res = await getHealth()
    healthInfo.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(fetchHealth)
</script>

<template>
  <div class="health-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title" data-eyebrow="STATUS · 系统监控">系统状态</h1>
      <el-button :loading="loading" size="small" @click="fetchHealth">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !healthInfo" :rows="4" animated />

    <template v-else-if="healthInfo">
      <!-- 状态主卡 -->
      <div class="status-card" :class="statusClass(healthInfo.status)">
        <div class="status-badge">
          <span class="status-dot"></span>
          <span class="status-text">{{ statusLabel(healthInfo.status) }}</span>
        </div>
        <div class="status-service">{{ healthInfo.service }}</div>
        <div class="status-time">
          <span class="time-label">检测时间</span>
          <span class="time-val">{{ formatTimestamp(healthInfo.timestamp) }}</span>
        </div>
      </div>

      <!-- 详情卡片网格 -->
      <div class="detail-grid">
        <div class="detail-card">
          <div class="detail-label">当前用户</div>
          <div class="detail-val">{{ userStore.username || '—' }}</div>
        </div>
        <div class="detail-card">
          <div class="detail-label">应用版本</div>
          <div class="detail-val mono">{{ appVersion }}</div>
        </div>
      </div>
    </template>

    <el-empty v-else description="暂无数据" />
  </div>
</template>

<style scoped lang="scss">
.health-view {
  max-width: 720px;
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

/* 状态主卡 */
.status-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-top: 2px solid var(--color-success);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: 14px;
  &.is-ok {
    border-top-color: var(--color-success);
  }
  &.is-err {
    border-top-color: var(--color-danger);
  }
  &.is-warn {
    border-top-color: var(--color-warning);
  }
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  align-self: flex-start;
  padding: 5px 12px;
  border-radius: var(--radius-full, 9999px);
  font-size: 13px;
  font-weight: 600;
  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
  }
}
.status-card.is-ok .status-badge {
  background: rgba(61, 92, 58, 0.08);
  color: var(--color-success);
  .status-dot {
    background: var(--color-success);
  }
}
.status-card.is-err .status-badge {
  background: rgba(139, 30, 30, 0.08);
  color: var(--color-danger);
  .status-dot {
    background: var(--color-danger);
  }
}
.status-card.is-warn .status-badge {
  background: rgba(139, 105, 20, 0.08);
  color: var(--color-warning);
  .status-dot {
    background: var(--color-warning);
  }
}
.status-service {
  font-size: 20px;
  font-weight: 600;
  font-family: var(--font-display);
  letter-spacing: -0.015em;
  color: var(--color-text-primary);
}
.status-time {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  .time-label {
    color: var(--color-text-secondary);
  }
  .time-val {
    color: var(--color-text-regular);
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
  }
}

/* 详情卡片网格 */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.detail-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 16px 18px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.detail-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}
.detail-val {
  font-size: 18px;
  font-weight: 600;
  font-family: var(--font-display);
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  &.mono {
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    color: var(--color-accent);
  }
}

/* ===== 响应式断点：平板 1024px ===== */
@media (max-width: 1024px) {
  .health-view {
    max-width: 100%;
  }
}

/* ===== 响应式断点：移动端 768px ===== */
@media (max-width: 768px) {
  .health-view {
    max-width: 100%;
    gap: 12px;
  }
  .page-header {
    padding: 2px 2px 0;
  }
  .page-title {
    font-size: 20px;
  }
  .status-card {
    padding: 18px 16px;
    gap: 12px;
  }
  .status-service {
    font-size: 18px;
  }
  .status-time {
    flex-wrap: wrap;
    gap: 6px;
    font-size: 12px;
  }
  .detail-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .detail-card {
    padding: 14px 16px;
  }
  .detail-val {
    font-size: 16px;
  }
}

/* ===== 响应式断点：小屏手机 480px ===== */
@media (max-width: 480px) {
  .status-card {
    padding: 14px 12px;
  }
  .status-service {
    font-size: 16px;
    word-break: break-all;
  }
  .detail-card {
    padding: 12px 14px;
  }
}
</style>
