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
const activeTab = ref('preview')

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
  activeTab.value = 'preview'
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

    <!-- 移动端水平滚动分类标签条（仅 768px 以下显示） -->
    <div class="cat-bar-mobile">
      <div
        v-for="c in categories"
        :key="c.key"
        class="cat-tag-mobile"
        :class="{ active: activeCategory === c.key }"
        @click="handleSelectCategory(c.key)"
      >
        {{ c.label }}
        <span class="cat-tag-count">{{ categoryCount(c.key) }}</span>
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
            <el-tag size="small" effect="light" class="cat-tag">{{ tpl.category }}</el-tag>
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
        <el-tabs v-model="activeTab" class="tpl-tabs">
          <!-- 标签页一：查看模板原文 -->
          <el-tab-pane label="模板原文" name="preview">
            <div class="tpl-preview">
              <div class="tpl-preview-meta">
                <el-tag size="small" effect="light">{{ currentTemplate?.category || '未分类' }}</el-tag>
                <span v-if="currentTemplate?.source" class="tpl-preview-source">来源：{{ currentTemplate.source }}</span>
              </div>
              <div class="tpl-preview-content">
                <MarkdownRenderer :content="currentTemplate?.rawText || ''" />
              </div>
              <div class="tpl-preview-actions">
                <el-button type="primary" plain @click="activeTab = 'generate'">前往生成文书 →</el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- 标签页二：生成文书 -->
          <el-tab-pane label="生成文书" name="generate">
            <div class="gen-section">
              <div class="gen-section-title">填写要素</div>
              <el-form v-if="elementFields.length" label-position="top" class="gen-form">
                <el-form-item v-for="f in elementFields" :key="f" :label="f" required>
                  <el-input v-model="elementValues[f]" :placeholder="`请输入${f}`" />
                </el-form-item>
              </el-form>
              <el-empty v-else description="该模板未定义要素字段，可直接生成" :image-size="56" />
            </div>

            <div class="gen-actions">
              <el-button type="primary" :loading="generating" @click="handleGenerate">
                {{ generating ? '生成中…' : '生成文书' }}
              </el-button>
              <template v-if="generatedContent">
                <el-button :icon="CopyDocument" plain @click="handleCopy">复制</el-button>
                <el-button :icon="Download" plain @click="handleDownload">下载</el-button>
              </template>
            </div>

            <div v-if="generatedContent" class="gen-result">
              <div class="gen-section-title">生成结果</div>
              <div class="result-box">
                <MarkdownRenderer :content="generatedContent" />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
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
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 10px 16px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: var(--color-bg);
  }
  &.active {
    background: var(--color-accent-light);
    // 左侧琥珀色竖条滑入动画
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 60%;
      border-radius: 0 2px 2px 0;
      background: var(--color-accent);
      animation: slideInLeft 0.3s var(--ease-out) both;
    }
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
    font-family: var(--font-serif);
    color: var(--color-primary);
    letter-spacing: -0.01em;
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
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
  background: var(--color-bg-card);
  display: flex;
  flex-direction: column;
  gap: 10px;
  // 入场动画（fade-in-up，staggered）
  animation: fadeInUp 0.45s var(--ease-out) both;
  transition: box-shadow 0.25s var(--ease-out), transform 0.25s var(--ease-out), border-color 0.25s var(--ease-out);
  &:nth-child(1) { animation-delay: 0.04s; }
  &:nth-child(2) { animation-delay: 0.08s; }
  &:nth-child(3) { animation-delay: 0.12s; }
  &:nth-child(4) { animation-delay: 0.16s; }
  &:nth-child(5) { animation-delay: 0.2s; }
  &:nth-child(n+6) { animation-delay: 0.24s; }
  // 右上角文档折角装饰（HyperUI 风格）
  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 0;
    height: 0;
    border-style: solid;
    border-width: 0 18px 18px 0;
    border-color: transparent rgba(200, 137, 62, 0.14) transparent transparent;
    transition: border-color 0.25s var(--ease-out);
  }
  &:hover {
    box-shadow: 0 4px 12px rgba(26, 23, 20, 0.06), 0 0 0 1px rgba(200, 137, 62, 0.12), 0 0 18px rgba(200, 137, 62, 0.1);
    transform: translateY(-2px);
    border-color: rgba(200, 137, 62, 0.4);
    &::after {
      border-right-color: rgba(200, 137, 62, 0.3);
    }
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
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tpl-card-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  :deep(.cat-tag) {
    background: var(--color-accent-light);
    color: var(--color-accent);
    border-color: transparent;
  }
}
.tpl-source {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tpl-card-raw {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--color-text-regular);
  font-family: var(--font-mono);
  background: var(--color-bg-soft);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  &::before {
    content: '\201C';
    color: var(--color-accent);
    margin-right: 2px;
    opacity: 0.7;
  }
}
.tpl-card-foot {
  display: flex;
  justify-content: flex-end;
}

/* 移动端水平滚动分类标签条：默认隐藏，768px 以下显示 */
.cat-bar-mobile {
  display: none;
}
.cat-tag-mobile {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  background: var(--color-bg-card);
  font-size: 13px;
  color: var(--color-text-regular);
  cursor: pointer;
  white-space: nowrap;
  transition: var(--transition-base);
  .cat-tag-count {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--color-text-secondary);
  }
  &.active {
    background: var(--color-accent-light);
    border-color: var(--color-accent);
    color: var(--color-primary);
    font-weight: 600;
    .cat-tag-count {
      color: var(--color-accent);
    }
  }
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .tpl-view {
    flex-direction: column;
    gap: 12px;
  }
  /* 隐藏左侧分类面板，改为顶部水平滚动标签条 */
  .cat-panel {
    display: none;
  }
  .cat-bar-mobile {
    display: flex;
    gap: 8px;
    padding: 10px 12px;
    background: var(--color-bg-card);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-card);
    box-shadow: var(--shadow-card);
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    &::-webkit-scrollbar {
      height: 0;
    }
  }
  .tpl-panel-header {
    padding: 12px 14px;
    .title {
      font-size: 15px;
    }
  }
  /* 模板网格单列 */
  .tpl-grid {
    grid-template-columns: 1fr;
    padding: 12px;
    gap: 12px;
  }
}

/* 生成对话框 */
.gen-body {
  display: flex;
  flex-direction: column;
}
.tpl-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
  :deep(.el-tabs__item) {
    font-family: var(--font-serif);
    font-size: 15px;
    letter-spacing: -0.01em;
  }
}
.tpl-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.tpl-preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}
.tpl-preview-source {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.tpl-preview-content {
  max-height: 420px;
  overflow-y: auto;
  padding: 16px 18px;
  background: var(--color-bg-soft);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-regular);
  white-space: pre-wrap;
}
.tpl-preview-actions {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
.gen-section {
  display: flex;
  flex-direction: column;
}
.gen-section-title {
  font-size: 14px;
  font-weight: 600;
  font-family: var(--font-serif);
  letter-spacing: -0.01em;
  color: var(--color-primary);
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}
.gen-form {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}
.gen-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: var(--color-bg-soft);
  border-radius: var(--radius-card);
}
.gen-result {
  border-top: 1px solid var(--color-border);
  padding-top: 18px;
}
.result-box {
  max-height: 360px;
  overflow-y: auto;
  padding: 16px 18px;
  background: var(--color-bg-soft);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
}
</style>
