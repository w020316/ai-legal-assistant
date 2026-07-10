<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Files, Document as DocumentIcon, CopyDocument, Download } from '@element-plus/icons-vue'
import {
  listTemplates,
  generateDocument,
  type TemplateVO,
} from '@/api'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer.vue'

// 分类列表
const categories = [
  { key: '', label: '全部' },
  { key: '劳动', label: '劳动合同' },
  { key: '租赁', label: '租赁合同' },
  { key: '借款', label: '借款合同' },
  { key: '采购', label: '采购合同' },
  { key: '服务', label: '服务合同' },
]

const activeCategory = ref('')
const templates = ref<TemplateVO[]>([])
const loading = ref(false)
// 分类缓存：key 为分类字符串（空串表示全部）
const cacheMap = ref(new Map<string, TemplateVO[]>())

// 生成对话框
const genDialogVisible = ref(false)
const currentTemplate = ref<TemplateVO | null>(null)
const elementFields = ref<string[]>([])
const elementValues = ref<Record<string, string>>({})
const generatedContent = ref('')
const generating = ref(false)

// 过滤后的模板列表
const filteredTemplates = computed(() => templates.value)

// 分类徽标计数
function categoryCount(key: string): number {
  if (!key) return templates.value.length
  return templates.value.filter((t) => t.category === key).length
}

// 加载模板列表（force=true 时强制重新加载，忽略缓存）
async function loadTemplates(category?: string, force = false) {
  const cacheKey = category || ''
  if (!force && cacheMap.value.has(cacheKey)) {
    templates.value = cacheMap.value.get(cacheKey) || []
    return
  }
  loading.value = true
  try {
    const res = await listTemplates(category)
    templates.value = res.data || []
    cacheMap.value.set(cacheKey, templates.value)
  } finally {
    loading.value = false
  }
}

// 选择分类
function handleSelectCategory(key: string) {
  activeCategory.value = key
  loadTemplates(key || undefined)
}

// 从模板原文解析占位字段 {{字段名}}
function parseFields(rawText: string): string[] {
  if (!rawText) return []
  const matches = rawText.match(/\{\{([^}]+)\}\}/g)
  if (!matches) return []
  const fields: string[] = []
  const seen = new Set<string>()
  for (const m of matches) {
    const name = m.replace(/[{}]/g, '').trim()
    if (name && !seen.has(name)) {
      seen.add(name)
      fields.push(name)
    }
  }
  return fields
}

// 打开生成对话框
function handleOpenGen(tpl: TemplateVO) {
  currentTemplate.value = tpl
  elementFields.value = parseFields(tpl.rawText)
  elementValues.value = {}
  for (const f of elementFields.value) {
    elementValues.value[f] = ''
  }
  generatedContent.value = ''
  genDialogVisible.value = true
}

// 生成文书
async function handleGenerate() {
  if (!currentTemplate.value) return
  // 校验必填
  for (const f of elementFields.value) {
    if (!elementValues.value[f]?.trim()) {
      ElMessage.warning(`请填写「${f}」`)
      return
    }
  }
  generating.value = true
  try {
    const res = await generateDocument(currentTemplate.value.id, {
      elements: { ...elementValues.value },
    })
    generatedContent.value = res.data.content
    ElMessage.success('文书已生成')
  } catch {
    // 错误已由拦截器提示
  } finally {
    generating.value = false
  }
}

// 复制结果
async function handleCopy() {
  if (!generatedContent.value) return
  try {
    await navigator.clipboard.writeText(generatedContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

// 下载结果
function handleDownload() {
  if (!generatedContent.value) return
  const blob = new Blob([generatedContent.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${currentTemplate.value?.title || '文书'}.md`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

onMounted(() => loadTemplates())
</script>

<template>
  <div class="tpl-view">
    <!-- 左侧分类 -->
    <div class="cat-panel">
      <div class="cat-header">
        <el-icon><Files /></el-icon>
        <span>文书分类</span>
      </div>
      <div class="cat-list">
        <div
          v-for="c in categories"
          :key="c.key"
          class="cat-item"
          :class="{ active: activeCategory === c.key }"
          @click="handleSelectCategory(c.key)"
        >
          <span class="cat-label">{{ c.label }}</span>
          <el-tag
            size="small"
            round
            effect="plain"
            :type="categoryCount(c.key) === 0 ? 'info' : undefined"
          >{{ categoryCount(c.key) }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 右侧模板列表 -->
    <div class="tpl-panel">
      <div class="tpl-panel-header">
        <span class="title">{{ categories.find((c) => c.key === activeCategory)?.label || '全部' }}模板</span>
        <el-button :loading="loading" size="small" @click="loadTemplates(activeCategory || undefined, true)">刷新</el-button>
      </div>

      <div v-loading="loading" class="tpl-grid">
        <div v-for="tpl in filteredTemplates" :key="tpl.id" class="tpl-card">
          <div class="tpl-card-head">
            <el-icon :size="20" color="var(--color-primary)"><DocumentIcon /></el-icon>
            <span class="tpl-title" :title="tpl.title">{{ tpl.title }}</span>
          </div>
          <div class="tpl-card-tags">
            <el-tag size="small" effect="light">{{ tpl.category }}</el-tag>
            <span class="tpl-source" :title="tpl.source">来源：{{ tpl.source || '—' }}</span>
          </div>
          <div class="tpl-card-raw">{{ tpl.rawText?.slice(0, 80) }}{{ tpl.rawText?.length > 80 ? '…' : '' }}</div>
          <div class="tpl-card-foot">
            <el-button type="primary" size="small" plain @click="handleOpenGen(tpl)">查看 / 生成</el-button>
          </div>
        </div>

        <el-empty
          v-if="!loading && filteredTemplates.length === 0"
          description="该分类暂无模板，敬请期待"
          :image-size="80"
          class="grid-empty"
        />
      </div>
    </div>

    <!-- 生成对话框 -->
    <el-dialog
      v-model="genDialogVisible"
      :title="currentTemplate?.title || '生成文书'"
      width="640px"
      destroy-on-close
    >
      <div class="gen-body">
        <!-- 要素表单 -->
        <div class="gen-section">
          <div class="gen-section-title">填写要素</div>
          <el-form v-if="elementFields.length" label-position="top" class="gen-form">
            <el-form-item v-for="f in elementFields" :key="f" :label="f" required>
              <el-input v-model="elementValues[f]" :placeholder="`请输入${f}`" />
            </el-form-item>
          </el-form>
          <el-empty v-else description="该模板未定义要素字段，可直接生成" :image-size="56" />
        </div>

        <!-- 生成操作 -->
        <div class="gen-actions">
          <el-button type="primary" :loading="generating" @click="handleGenerate">
            {{ generating ? '生成中…' : '生成文书' }}
          </el-button>
          <template v-if="generatedContent">
            <el-button :icon="CopyDocument" plain @click="handleCopy">复制</el-button>
            <el-button :icon="Download" plain @click="handleDownload">下载</el-button>
          </template>
        </div>

        <!-- 生成结果 -->
        <div v-if="generatedContent" class="gen-result">
          <div class="gen-section-title">生成结果</div>
          <div class="result-box">
            <MarkdownRenderer :content="generatedContent" />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tpl-view {
  display: flex;
  height: 100%;
  gap: 16px;
}

/* 左侧分类 */
.cat-panel {
  width: 200px;
  flex-shrink: 0;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.cat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  color: var(--color-primary);
}
.cat-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.cat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: var(--color-bg);
  }
  &.active {
    background: #eef3f8;
    .cat-label {
      color: var(--color-primary);
      font-weight: 600;
    }
  }
}
.cat-label {
  font-size: 14px;
  color: var(--color-text-regular);
}

/* 右侧模板 */
.tpl-panel {
  flex: 1;
  min-width: 0;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tpl-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
  .title {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-primary);
  }
}
.tpl-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  align-content: start;
}
.grid-empty {
  grid-column: 1 / -1;
}
.tpl-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
  background: var(--color-bg-card);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: box-shadow 0.15s, transform 0.15s;
  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
  }
}
.tpl-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tpl-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tpl-card-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tpl-source {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tpl-card-raw {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-regular);
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tpl-card-foot {
  display: flex;
  justify-content: flex-end;
}

/* 生成对话框 */
.gen-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.gen-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 8px;
}
.gen-form {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}
.gen-actions {
  display: flex;
  gap: 8px;
}
.gen-result {
  border-top: 1px solid var(--color-border);
  padding-top: 16px;
}
.result-box {
  max-height: 360px;
  overflow-y: auto;
  padding: 16px;
  background: var(--color-bg);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
}
</style>
