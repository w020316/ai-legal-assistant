<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { searchCases, type CaseVO, type CaseSearchRequest } from '@/api'

// 案由选项
const causeOptions = [
  '合同纠纷',
  '劳动争议',
  '侵权责任',
  '婚姻家庭',
  '房产纠纷',
  '借贷纠纷',
  '知识产权',
  '公司股权',
]

// 法院层级选项
const courtLevelOptions = [
  '最高人民法院',
  '高级人民法院',
  '中级人民法院',
  '基层人民法院',
]

// 年份选项（当前年份到 2015 年，不包含未来年份）
const currentYear = new Date().getFullYear()
const yearOptions = Array.from({ length: currentYear - 2015 + 1 }, (_, i) => currentYear - i)

// 搜索表单
const form = reactive<CaseSearchRequest>({
  keyword: '',
  cause: '',
  courtLevel: '',
  year: undefined,
  page: 1,
  size: 20,
})

const cases = ref<CaseVO[]>([])
const loading = ref(false)
const searched = ref(false)

// 请求 ID：用于丢弃过期的搜索响应，避免旧结果覆盖新结果
const requestId = ref(0)
// 防抖计时器
let debounceTimer: ReturnType<typeof setTimeout> | null = null

// 详情抽屉
const drawerVisible = ref(false)
const currentCase = ref<CaseVO | null>(null)

// 执行检索
async function handleSearch() {
  const currentId = ++requestId.value
  loading.value = true
  searched.value = true
  try {
    const params: CaseSearchRequest = {
      keyword: form.keyword?.trim() || undefined,
      cause: form.cause || undefined,
      courtLevel: form.courtLevel || undefined,
      year: form.year || undefined,
      page: form.page,
      size: form.size,
    }
    const res = await searchCases(params)
    // 仅接受最新请求的响应，避免旧结果覆盖新结果
    if (currentId !== requestId.value) return
    cases.value = res.data || []
  } finally {
    if (currentId === requestId.value) {
      loading.value = false
    }
  }
}

// 简单防抖：300ms 内重复触发只执行最后一次
function debouncedSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    handleSearch()
    debounceTimer = null
  }, 300)
}

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

// 重置
function handleReset() {
  form.keyword = ''
  form.cause = ''
  form.courtLevel = ''
  form.year = undefined
  handleSearch()
}

// 查看详情
function handleViewDetail(c: CaseVO) {
  currentCase.value = c
  drawerVisible.value = true
}

onMounted(handleSearch)
</script>

<template>
  <div class="case-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">案例检索</h1>
    </div>

    <!-- 顶部搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="form.keyword"
        placeholder="输入关键词，如合同解除、违约金、竞业限制…"
        clearable
        class="keyword-input"
        @keyup.enter="debouncedSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="form.cause" placeholder="案由" clearable class="filter-item">
        <el-option v-for="c in causeOptions" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="form.courtLevel" placeholder="法院层级" clearable class="filter-item">
        <el-option v-for="c in courtLevelOptions" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="form.year" placeholder="年份" clearable class="filter-item year-item">
        <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
    <div class="search-hint">提示：输入关键词可进行语义检索，或按案由/法院/年份筛选</div>

    <!-- 案例列表 -->
    <div class="case-list-wrap">
      <div class="list-meta">
        <span>共找到 {{ cases.length }} 条案例</span>
      </div>
      <div v-loading="loading" class="case-list">
        <div v-for="c in cases" :key="c.id" class="case-card" @click="handleViewDetail(c)">
          <div class="case-card-head">
            <span class="case-title" :title="c.title">{{ c.title }}</span>
            <el-tag size="small" effect="light">{{ c.caseCause || '未分类' }}</el-tag>
          </div>
          <div class="case-card-meta">
            <span>{{ c.court || '—' }}</span>
            <span class="dot">·</span>
            <span>{{ c.year ? c.year + '年' : '—' }}</span>
            <span v-if="c.source" class="dot">·</span>
            <span v-if="c.source" class="source">{{ c.source }}</span>
          </div>
          <div class="case-card-summary">{{ c.summary || '暂无摘要' }}</div>
        </div>

        <el-empty
          v-if="!loading && searched && cases.length === 0"
          description="未找到相关案例，试试调整搜索条件或清除筛选"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="案例详情" size="520px" direction="rtl">
      <template v-if="currentCase">
        <div class="detail">
          <h2 class="detail-title">{{ currentCase.title }}</h2>
          <div class="detail-tags">
            <el-tag size="small" effect="light">{{ currentCase.caseCause || '未分类' }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ currentCase.court || '—' }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ currentCase.year ? currentCase.year + '年' : '—' }}</el-tag>
          </div>
          <div v-if="currentCase.source" class="detail-source">
            来源：<a v-if="currentCase.source.startsWith('http')" :href="currentCase.source" target="_blank" rel="noopener">{{ currentCase.source }}</a>
            <span v-else>{{ currentCase.source }}</span>
          </div>
          <div class="detail-section">
            <div class="detail-section-title">案件摘要</div>
            <div class="detail-text">{{ currentCase.summary }}</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.case-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
}

/* 页面标题 */
.page-header {
  padding: 4px 4px 0;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-primary);
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  flex-wrap: wrap;
}
.keyword-input {
  flex: 1;
  min-width: 280px;
}
.filter-item {
  width: 160px;
}
.year-item {
  width: 120px;
}
.search-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 0 20px 8px;
}

/* 列表 */
.case-list-wrap {
  flex: 1;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.list-meta {
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.case-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.case-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
  background: var(--color-bg-card);
  cursor: pointer;
  transition: box-shadow 0.15s, transform 0.15s, border-color 0.15s;
  &:hover {
    box-shadow: 0 4px 12px rgba(11, 37, 69, 0.06);
    transform: translateY(-1px);
    border-color: var(--color-primary-light);
  }
}
.case-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.case-title {
  font-size: 15px;
  font-weight: 600;
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.case-card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  .source {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 200px;
  }
}
.dot {
  color: var(--color-text-secondary);
}
.case-card-summary {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 详情抽屉 */
.detail {
  padding: 0 4px;
}
.detail-title {
  font-size: 18px;
  font-weight: 600;
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-primary);
  line-height: 1.5;
  margin-bottom: 12px;
}
:deep(.el-drawer__title) {
  font-family: var(--font-serif);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-source {
  font-size: 13px;
  color: var(--color-text-regular);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
  a {
    color: var(--color-accent);
    text-decoration: underline;
    word-break: break-all;
  }
}
.detail-section {
  margin-bottom: 20px;
}
.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 8px;
}
.detail-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-regular);
  white-space: pre-wrap;
}
</style>
