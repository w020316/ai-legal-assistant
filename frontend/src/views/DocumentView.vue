<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'
import { UploadFilled, Document as DocumentIcon, ArrowDown } from '@element-plus/icons-vue'
import {
  listDocuments,
  uploadDocument,
  analyzeDocument,
  getDocumentAnalysis,
  type UserDocumentVO,
  type DocumentAnalysis,
} from '@/api'

const documents = ref<UserDocumentVO[]>([])
const currentDoc = ref<UserDocumentVO | null>(null)
const analysis = ref<DocumentAnalysis | null>(null)
const loading = ref(false)
const uploading = ref(false)
const analyzing = ref(false)

// 文档分析进度反馈
const analyzeProgress = ref<{ stage: string; percent: number } | null>(null)
let analyzeTimer: ReturnType<typeof setInterval> | null = null
const analyzeStages = [
  { stage: '正在提取文档内容', percent: 10 },
  { stage: '正在识别关键条款', percent: 30 },
  { stage: '正在分析法律风险', percent: 55 },
  { stage: '正在生成修改建议', percent: 80 },
  { stage: '正在整理分析报告', percent: 95 },
]

// 大白话折叠面板：记录每个风险点是否展开
const plainExpanded = ref<Record<number, boolean>>({})

// 大白话总览折叠
const plainSummaryExpanded = ref(false)

// 缺失条款折叠
const missingExpanded = ref(false)

// 文件大小格式化
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

// 文件类型图标颜色：使用设计系统语义色
function fileIconColor(type: string): string {
  const t = type.toLowerCase()
  if (t.includes('pdf')) return '#8B1E1E' // var(--color-danger)
  if (t.includes('doc')) return '#4A4038' // var(--color-primary-soft)
  return '#3D5C3A' // var(--color-success)
}

// 分析状态 -> 标签类型
function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const s = status.toLowerCase()
  if (s.includes('done') || s.includes('complete')) return 'success'
  if (s.includes('process') || s.includes('running')) return 'warning'
  if (s.includes('fail')) return 'danger'
  return 'info'
}

// 分析状态 -> 文案
function statusLabel(status: string): string {
  const s = status.toLowerCase()
  if (s.includes('done') || s.includes('complete')) return '已完成'
  if (s.includes('process') || s.includes('running')) return '分析中'
  if (s.includes('fail')) return '失败'
  if (s.includes('pending')) return '待分析'
  return status
}

// 风险等级 -> 标签类型
function riskTagType(level: string): 'danger' | 'warning' | 'success' | 'info' {
  const l = level.toLowerCase()
  if (l.includes('high') || l === '高') return 'danger'
  if (l.includes('medium') || l === '中') return 'warning'
  if (l.includes('low') || l === '低') return 'success'
  return 'info'
}

// 风险等级文案
function riskLabel(level: string): string {
  const l = level.toLowerCase()
  if (l.includes('high') || l === '高') return '高风险'
  if (l.includes('medium') || l === '中') return '中风险'
  if (l.includes('low') || l === '低') return '低风险'
  return level
}

// 评分 -> 颜色
function scoreColor(score?: number): string {
  if (score == null) return 'var(--color-text-secondary)'
  if (score >= 70) return 'var(--color-success)'
  if (score >= 50) return 'var(--color-warning)'
  return 'var(--color-danger)'
}

// 评分 -> 等级文案
function scoreLabel(score?: number): string {
  if (score == null) return '—'
  if (score >= 90) return '低风险·可签'
  if (score >= 70) return '中低风险·建议小改'
  if (score >= 50) return '中高风险·需重点修改'
  return '高风险·不建议签署'
}

// 评分 -> 环形进度条状态
function scoreStatus(score?: number): 'success' | 'warning' | 'exception' | undefined {
  if (score == null) return undefined
  if (score >= 70) return 'success'
  if (score >= 50) return 'warning'
  return 'exception'
}

// 缺失条款数量
const missingCount = computed(() => analysis.value?.missingClauses?.length || 0)

// 高重要性缺失条款数量
const missingHighCount = computed(
  () =>
    analysis.value?.missingClauses?.filter((m) =>
      m.importance?.toLowerCase().includes('high') || m.importance === '高'
    ).length || 0
)

// 切换大白话折叠
function togglePlain(idx: number) {
  plainExpanded.value[idx] = !plainExpanded.value[idx]
}

// 上传前校验
function beforeUpload(file: UploadRawFile): boolean {
  const name = file.name.toLowerCase()
  const isValidType =
    name.endsWith('.pdf') ||
    name.endsWith('.docx') ||
    file.type === 'application/pdf' ||
    file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  if (!isValidType) {
    ElMessage.error('仅支持 PDF / DOCX 文件')
    return false
  }
  const isLt20M = file.size / 1024 / 1024 < 20
  if (!isLt20M) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

// 自定义上传
async function customUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  try {
    const res = await uploadDocument(options.file)
    ElMessage.success('上传成功')
    await loadDocuments()
    const doc = documents.value.find((d) => d.id === res.data.id)
    if (doc) handleSelectDoc(doc)
  } catch {
    // 错误已由响应拦截器统一提示
  } finally {
    uploading.value = false
  }
}

// 加载文档列表
async function loadDocuments() {
  loading.value = true
  try {
    const res = await listDocuments()
    documents.value = res.data || []
  } finally {
    loading.value = false
  }
}

// 选择文档
async function handleSelectDoc(doc: UserDocumentVO) {
  currentDoc.value = doc
  analysis.value = null
  plainExpanded.value = {}
  plainSummaryExpanded.value = false
  missingExpanded.value = false
  // 若已完成分析，拉取分析结果
  if (statusTagType(doc.analysisStatus) === 'success') {
    await fetchAnalysis(doc.id)
  }
}

// 拉取分析结果
async function fetchAnalysis(id: number) {
  try {
    const res = await getDocumentAnalysis(id)
    analysis.value = res.data
    plainExpanded.value = {}
  } catch {
    analysis.value = null
  }
}

// 触发分析
async function handleAnalyze() {
  if (!currentDoc.value) return
  const docId = currentDoc.value.id
  analyzing.value = true
  // 开始进度反馈
  analyzeProgress.value = { stage: '正在提取文档内容', percent: 10 }
  let stageIdx = 0
  // 每 2 秒推进进度阶段
  analyzeTimer = setInterval(() => {
    stageIdx = Math.min(stageIdx + 1, analyzeStages.length - 1)
    analyzeProgress.value = { ...analyzeStages[stageIdx] }
  }, 2000)
  try {
    await analyzeDocument(docId)
    ElMessage.success('分析任务已启动，正在生成报告…')
    // 等待 3 秒后开始轮询分析结果
    await new Promise((resolve) => setTimeout(resolve, 3000))
    // 每 3 秒轮询一次，最多 10 次
    for (let i = 0; i < 10; i++) {
      await fetchAnalysis(docId)
      if (analysis.value) break
      await new Promise((resolve) => setTimeout(resolve, 3000))
    }
    if (currentDoc.value && currentDoc.value.id === docId) {
      currentDoc.value.analysisStatus = 'completed'
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    analyzing.value = false
    // 清理 timer
    if (analyzeTimer) {
      clearInterval(analyzeTimer)
      analyzeTimer = null
    }
    analyzeProgress.value = null
  }
}

onMounted(loadDocuments)

onUnmounted(() => {
  if (analyzeTimer) {
    clearInterval(analyzeTimer)
    analyzeTimer = null
  }
})
</script>

<template>
  <div class="doc-view">
    <!-- 左侧：上传 + 文档列表 -->
    <div class="left-panel">
      <div class="upload-wrap">
        <el-upload
          drag
          multiple
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="customUpload"
          accept=".pdf,.docx"
        >
          <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件至此，或<em>点击上传</em></div>
          <template #tip>
            <div class="upload-tip">支持 PDF / DOCX，单个文件不超过 20MB</div>
          </template>
        </el-upload>
      </div>

      <div class="doc-list">
        <div class="list-header">
          <span>文档列表</span>
          <el-tag size="small" round>{{ documents.length }}</el-tag>
        </div>
        <div v-loading="loading" class="list-body">
          <div
            v-for="doc in documents"
            :key="doc.id"
            class="doc-item"
            :class="{ active: currentDoc?.id === doc.id }"
            @click="handleSelectDoc(doc)"
          >
            <el-icon :color="fileIconColor(doc.fileType)" :size="22"><DocumentIcon /></el-icon>
            <div class="doc-meta">
              <div class="doc-name" :title="doc.filename">{{ doc.filename }}</div>
              <div class="doc-sub">
                <span>{{ formatSize(doc.fileSize) }}</span>
                <span>{{ doc.createdAt?.slice(0, 10) }}</span>
              </div>
            </div>
            <el-tag :type="statusTagType(doc.analysisStatus)" size="small" effect="light">
              {{ statusLabel(doc.analysisStatus) }}
            </el-tag>
          </div>
          <el-empty v-if="!loading && documents.length === 0" description="暂无文档" :image-size="64" />
        </div>
      </div>
    </div>

    <!-- 右侧：预览 + 分析报告 -->
    <div class="right-panel">
      <template v-if="currentDoc">
        <!-- 文档信息头 -->
        <div class="doc-header">
          <div class="doc-title">
            <el-icon :color="fileIconColor(currentDoc.fileType)" :size="22"><DocumentIcon /></el-icon>
            <span :title="currentDoc.filename">{{ currentDoc.filename }}</span>
          </div>
          <div class="doc-actions">
            <el-tag :type="statusTagType(currentDoc.analysisStatus)" size="small" effect="light">
              {{ statusLabel(currentDoc.analysisStatus) }}
            </el-tag>
            <el-button
              type="primary"
              :loading="analyzing"
              :disabled="statusTagType(currentDoc.analysisStatus) === 'success'"
              @click="handleAnalyze"
            >
              {{ statusTagType(currentDoc.analysisStatus) === 'success' ? '已分析' : '开始分析' }}
            </el-button>
          </div>
        </div>
        <div class="doc-info-bar">
          <div class="info-item">
            <span class="info-label">类型</span>
            <span class="info-val">{{ currentDoc.fileType || '—' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">大小</span>
            <span class="info-val">{{ formatSize(currentDoc.fileSize) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">上传时间</span>
            <span class="info-val">{{ currentDoc.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
          </div>
        </div>

        <!-- 分析进度反馈 -->
        <div v-if="analyzeProgress" class="analyze-progress">
          <el-progress :percentage="analyzeProgress.percent" striped striped-flow />
          <div class="progress-stage">{{ analyzeProgress.stage }}</div>
        </div>

        <!-- 分析报告 -->
        <div v-loading="analyzing" class="report-area">
          <template v-if="analysis">
            <!-- 评分卡片：安全评分 + 风险等级 + 通俗化总览 -->
            <div class="score-card" :class="'score-card-' + scoreStatus(analysis.score)">
              <div class="score-ring">
                <el-progress
                  type="dashboard"
                  :percentage="analysis.score ?? 0"
                  :width="120"
                  :stroke-width="8"
                  :status="scoreStatus(analysis.score)"
                >
                  <template #default>
                    <div class="score-num">
                      <span class="num">{{ analysis.score ?? '—' }}</span>
                      <span class="unit">/100</span>
                    </div>
                  </template>
                </el-progress>
              </div>
              <div class="score-meta">
                <div class="score-header">
                  <span class="score-eyebrow">SAFETY · 安全评分</span>
                  <el-tag
                    v-if="analysis.riskLevel"
                    :type="riskTagType(analysis.riskLevel)"
                    size="default"
                    effect="dark"
                    round
                  >
                    {{ riskLabel(analysis.riskLevel) }}
                  </el-tag>
                </div>
                <div class="score-label" :style="{ color: scoreColor(analysis.score) }">
                  {{ scoreLabel(analysis.score) }}
                </div>
                <div class="score-summary">{{ analysis.summary }}</div>
              </div>
            </div>

            <!-- 大白话总览（折叠） -->
            <div v-if="analysis.plainSummary" class="report-block plain-summary-block">
              <div class="block-title" @click="plainSummaryExpanded = !plainSummaryExpanded">
                <span class="title-text">大白话说合同</span>
                <span class="title-hint">{{ plainSummaryExpanded ? '收起' : '展开' }}</span>
                <el-icon class="title-arrow" :class="{ expanded: plainSummaryExpanded }">
                  <ArrowDown />
                </el-icon>
              </div>
              <transition name="plain">
                <div v-show="plainSummaryExpanded" class="plain-summary-text">
                  {{ analysis.plainSummary }}
                </div>
              </transition>
            </div>

            <!-- 风险点 -->
            <div class="report-block">
              <div class="block-title">
                风险点
                <el-tag size="small" round>{{ analysis.riskPoints?.length || 0 }}</el-tag>
              </div>
              <div v-if="analysis.riskPoints?.length" class="risk-list">
                <div
                  v-for="(rp, i) in analysis.riskPoints"
                  :key="i"
                  class="risk-card"
                  :class="'risk-' + riskTagType(rp.level)"
                >
                  <div class="risk-card-head">
                    <el-tag :type="riskTagType(rp.level)" size="small" effect="dark">
                      {{ riskLabel(rp.level) }}
                    </el-tag>
                    <span class="risk-clause">{{ rp.clause }}</span>
                  </div>
                  <div class="risk-field">
                    <span class="field-label">问题：</span>{{ rp.issue }}
                  </div>
                  <div class="risk-field">
                    <span class="field-label">建议：</span>{{ rp.suggestion }}
                  </div>
                  <div class="risk-field legal">
                    <span class="field-label">法律依据：</span>{{ rp.legalBasis }}
                  </div>
                  <!-- 大白话折叠面板 -->
                  <div
                    v-if="rp.plainExplanation"
                    class="plain-toggle"
                    @click="togglePlain(i)"
                  >
                    <span class="plain-toggle-text">大白话说</span>
                    <el-icon class="plain-toggle-arrow" :class="{ expanded: plainExpanded[i] }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                  <transition name="plain">
                    <div v-show="plainExpanded[i]" class="plain-explanation">
                      {{ rp.plainExplanation }}
                    </div>
                  </transition>
                </div>
              </div>
              <el-empty v-else description="未发现风险点" :image-size="64" />
            </div>

            <!-- 缺失条款提醒 -->
            <div v-if="missingCount > 0" class="report-block missing-block">
              <div class="block-title missing-title" @click="missingExpanded = !missingExpanded">
                <span class="title-text">
                  缺失条款提醒
                  <el-tag type="warning" size="small" round>{{ missingCount }}</el-tag>
                  <span v-if="missingHighCount > 0" class="title-sub">
                    含 {{ missingHighCount }} 项高重要性
                  </span>
                </span>
                <el-icon class="title-arrow" :class="{ expanded: missingExpanded }">
                  <ArrowDown />
                </el-icon>
              </div>
              <transition name="plain">
                <div v-show="missingExpanded" class="missing-list">
                  <div
                    v-for="(m, i) in analysis.missingClauses"
                    :key="i"
                    class="missing-card"
                    :class="'missing-' + riskTagType(m.importance)"
                  >
                    <div class="missing-head">
                      <el-tag :type="riskTagType(m.importance)" size="small" effect="plain">
                        {{ riskLabel(m.importance) }}
                      </el-tag>
                      <span class="missing-name">{{ m.name }}</span>
                    </div>
                    <div class="missing-field">
                      <span class="field-label">缺失风险：</span>{{ m.risk }}
                    </div>
                    <div class="missing-field">
                      <span class="field-label">补充建议：</span>{{ m.suggestion }}
                    </div>
                  </div>
                </div>
              </transition>
            </div>
          </template>

          <div v-else-if="!analyzing" class="report-empty">
            <el-empty description="暂无分析报告，点击「开始分析」生成">
              <el-button type="primary" @click="handleAnalyze">开始分析</el-button>
            </el-empty>
          </div>
        </div>
      </template>

      <div v-else class="right-empty">
        <el-icon :size="56" color="var(--color-text-secondary)"><DocumentIcon /></el-icon>
        <h3>选择左侧文档查看分析报告</h3>
        <p>上传合同/协议文件，AI 将自动识别风险条款并给出修改建议</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.doc-view {
  display: flex;
  height: 100%;
  gap: 16px;
}

/* 左侧 */
.left-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.upload-wrap {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 12px;
  box-shadow: var(--shadow-card);
}
.upload-wrap :deep(.el-upload-dragger) {
  padding: 18px 12px;
  border-radius: var(--radius-button);
  border-color: var(--color-accent);
  background: var(--color-bg-card);
  transition: var(--transition-base);
  &:hover {
    border-color: var(--color-accent);
    background: var(--color-accent-light);
  }
}
.upload-wrap :deep(.el-upload-dragger .el-icon--upload) {
  color: var(--color-accent);
}
.upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}
.doc-list {
  flex: 1;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}
.list-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: var(--color-bg);
  }
  &.active {
    background: var(--color-accent-light);
  }
}
.doc-meta {
  flex: 1;
  min-width: 0;
}
.doc-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.doc-sub {
  display: flex;
  gap: 12px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 右侧 */
.right-panel {
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
.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.doc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  font-family: var(--font-display);
  letter-spacing: -0.015em;
  color: var(--color-primary);
  min-width: 0;
  span {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
.doc-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.doc-info-bar {
  display: flex;
  padding: 12px 20px;
  background: var(--color-bg-soft);
  border-bottom: 1px solid var(--color-border);
  .info-item {
    display: flex;
    align-items: baseline;
    gap: 6px;
    padding: 0 18px;
    font-size: 13px;
    &:first-child {
      padding-left: 0;
    }
    & + .info-item {
      border-left: 1px solid var(--color-border);
    }
  }
  .info-label {
    color: var(--color-text-secondary);
    flex-shrink: 0;
  }
  .info-val {
    color: var(--color-text-regular);
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
  }
}
/* 分析进度反馈区 */
.analyze-progress {
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-soft);
}
.progress-stage {
  margin-top: 10px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent);
  animation: pulse-stage 1.6s var(--ease-out) infinite;
}
@keyframes pulse-stage {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
.report-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.report-block {
  margin-bottom: 24px;
}
.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  font-family: var(--font-display);
  letter-spacing: -0.015em;
  color: var(--color-primary);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border);
  position: relative;
  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: -1px;
    width: 32px;
    height: 2px;
    background: var(--color-accent);
  }
  &.clickable {
    cursor: pointer;
    user-select: none;
    transition: color 0.15s;
    &:hover {
      color: var(--color-accent);
    }
  }
  .title-text {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .title-hint {
    margin-left: auto;
    font-size: 12px;
    font-weight: 400;
    color: var(--color-text-secondary);
    font-family: var(--font-mono);
    letter-spacing: 0.06em;
  }
  .title-sub {
    font-size: 12px;
    font-weight: 400;
    color: var(--color-warning);
    font-family: var(--font-serif);
    font-style: italic;
  }
  .title-arrow {
    margin-left: auto;
    font-size: 14px;
    color: var(--color-text-secondary);
    transition: transform 0.25s var(--ease-out);
    &.expanded {
      transform: rotate(180deg);
    }
  }
}

/* 评分卡片 */
.score-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  margin-bottom: 24px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-accent);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: border-left-color 0.25s;
  &.score-card-success {
    border-left-color: var(--color-success);
  }
  &.score-card-warning {
    border-left-color: var(--color-warning);
  }
  &.score-card-exception {
    border-left-color: var(--color-danger);
  }
}
.score-ring {
  flex-shrink: 0;
  :deep(.el-progress-dashboard__container) {
    width: 120px;
    height: 120px;
  }
}
.score-num {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1;
  .num {
    font-family: var(--font-display);
    font-size: 36px;
    font-weight: 700;
    letter-spacing: -0.03em;
    color: var(--color-primary);
  }
  .unit {
    margin-top: 4px;
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--color-text-secondary);
    letter-spacing: 0.1em;
  }
}
.score-meta {
  flex: 1;
  min-width: 0;
}
.score-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.score-eyebrow {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-accent);
}
.score-label {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}
.score-summary {
  font-size: 13px;
  line-height: 1.75;
  color: var(--color-text-regular);
  white-space: pre-wrap;
}

/* 大白话总览折叠 */
.plain-summary-block {
  .block-title {
    cursor: pointer;
    user-select: none;
    transition: color 0.15s;
    &:hover {
      color: var(--color-accent);
    }
  }
  .plain-summary-text {
    padding: 14px 18px;
    background: var(--color-accent-light);
    border-left: 3px solid var(--color-accent);
    border-radius: var(--radius-card);
    font-family: var(--font-serif);
    font-size: 14px;
    line-height: 1.85;
    color: var(--color-text-regular);
    font-style: italic;
  }
}

/* 大白话折叠面板（风险点内） */
.plain-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  padding: 4px 10px;
  border: 1px dashed var(--color-accent);
  border-radius: var(--radius-button);
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
  .plain-toggle-text {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.1em;
    color: var(--color-accent);
    text-transform: uppercase;
  }
  .plain-toggle-arrow {
    font-size: 12px;
    color: var(--color-accent);
    transition: transform 0.25s var(--ease-out);
    &.expanded {
      transform: rotate(180deg);
    }
  }
  &:hover {
    background: var(--color-accent-light);
  }
}
.plain-explanation {
  margin-top: 10px;
  padding: 12px 14px;
  background: var(--color-bg-soft);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius-button);
  font-family: var(--font-serif);
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-regular);
  font-style: italic;
}

/* 缺失条款 */
.missing-block {
  .missing-title {
    cursor: pointer;
    user-select: none;
    transition: color 0.15s;
    &:hover {
      color: var(--color-warning);
    }
    &::after {
      background: var(--color-warning);
    }
  }
}
.missing-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.missing-card {
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-warning);
  border-radius: var(--radius-card);
  background: rgba(139, 105, 20, 0.025);
  &.missing-danger {
    border-left-color: var(--color-danger);
    background: rgba(139, 30, 30, 0.025);
  }
  &.missing-success {
    border-left-color: var(--color-success);
    background: rgba(61, 92, 58, 0.025);
  }
}
.missing-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.missing-name {
  font-weight: 600;
  color: var(--color-text-primary);
}
.missing-field {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-regular);
  margin-top: 4px;
}

/* 折叠过渡动画 */
.plain-enter-active,
.plain-leave-active {
  transition: all 0.25s var(--ease-out);
  overflow: hidden;
}
.plain-enter-from,
.plain-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-4px);
}
.plain-enter-to,
.plain-leave-from {
  opacity: 1;
  max-height: 600px;
}

.summary-block {
  padding: 16px 18px;
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius-card);
}
.summary-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-regular);
  white-space: pre-wrap;
}
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.risk-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
  background: var(--color-bg-card);
  transition: box-shadow 0.15s;
  &:hover {
    box-shadow: 0 2px 8px rgba(20, 17, 15, 0.06);
  }
  &.risk-danger {
    background: rgba(139, 30, 30, 0.035);
    border-top: 2px solid var(--color-danger);
  }
  &.risk-warning {
    background: rgba(139, 105, 20, 0.035);
    border-top: 2px solid var(--color-warning);
  }
  &.risk-success {
    background: rgba(61, 92, 58, 0.035);
    border-top: 2px solid var(--color-success);
  }
}
.risk-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.risk-clause {
  font-weight: 600;
  color: var(--color-text-primary);
}
.risk-field {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-regular);
  margin-top: 6px;
}
.field-label {
  color: var(--color-text-secondary);
  font-weight: 500;
}
.risk-field.legal .field-label {
  color: var(--color-accent);
}
.report-empty,
.right-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--color-text-regular);
  h3 {
    margin: 16px 0 8px;
    font-family: var(--font-display);
    font-size: 20px;
    font-weight: 600;
    letter-spacing: -0.015em;
    color: var(--color-primary);
  }
  p {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 13px;
    color: var(--color-text-secondary);
    max-width: 36ch;
  }
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .doc-view {
    flex-direction: column;
    gap: 12px;
  }
  /* 左右布局改为上下布局 */
  .left-panel {
    width: 100%;
    flex-shrink: 1;
  }
  .right-panel {
    flex: 1;
  }
  /* doc-info-bar 改为垂直排列 */
  .doc-info-bar {
    flex-direction: column;
    gap: 8px;
    padding: 12px 16px;
    .info-item {
      padding: 0;
      & + .info-item {
        border-left: none;
      }
    }
  }
  .doc-header {
    padding: 12px 14px;
  }
  .doc-title {
    font-size: 15px;
  }
  .report-area {
    padding: 14px;
  }
  .doc-list {
    max-height: 240px;
  }
  /* 评分卡片改为纵向布局 */
  .score-card {
    flex-direction: column;
    gap: 14px;
    padding: 16px;
    text-align: center;
  }
  .score-header {
    justify-content: center;
  }
  .score-label {
    font-size: 17px;
  }
}
</style>
