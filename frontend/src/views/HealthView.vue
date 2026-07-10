<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api'

const loading = ref(false)
const healthInfo = ref<{ status: string; service: string; timestamp: string } | null>(null)

// 时间戳格式化：ISO 时间戳转为本地时间 yyyy-MM-dd HH:mm:ss CST
function formatTimestamp(timestamp: string): string {
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} CST`
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
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>系统健康状态</span>
          <el-button :loading="loading" size="small" @click="fetchHealth">刷新</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading && !healthInfo" :rows="4" animated />
      <el-descriptions v-else-if="healthInfo" :column="1" border>
        <el-descriptions-item label="服务状态">
          <el-tag type="success">{{ healthInfo.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="服务名称">{{ healthInfo.service }}</el-descriptions-item>
        <el-descriptions-item label="时间戳">{{ formatTimestamp(healthInfo.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="当前用户">admin</el-descriptions-item>
        <el-descriptions-item label="版本号">v1.0.0</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无数据" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.health-view {
  max-width: 720px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
