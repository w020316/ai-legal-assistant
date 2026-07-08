<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'
import { UploadFilled, Document as DocumentIcon } from '@element-plus/icons-vue'
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

// 文件大小格式化
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

// 文件类型图标颜色
function fileIconColor(type: string): string {
  const t = type.toLowerCase()
  if (t.includes('pdf')) return '#C53030'
  if (t.includes('doc')) return '#2D5485'
  return '#2C7A7B'
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
  } catch {
    analysis.value = null
  }
}

// 触发分析
async function handleAnalyze() {
  if (!currentDoc.value) return
  analyzing.value = true
  try {
    await analyzeDocument(currentDoc.value.id)
    ElMessage.success('分析任务已启动，正在生成报告…')
    await fetchAnalysis(currentDoc.value.id)
    if (currentDoc.value) {
      currentDoc.value.analysisStatus = 'completed'
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    analyzing.value = false
  }
}

onMounted(loadDocuments)
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
          <span>类型：{{ currentDoc.fileType || '—' }}</span>
          <span>大小：{{ formatSize(currentDoc.fileSize) }}</span>
          <span>上传时间：{{ currentDoc.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
        </div>

        <!-- 分析报告 -->
        <div v-loading="analyzing" class="report-area">
          <template v-if="analysis">
            <div class="report-block summary-block">
              <div class="block-title">分析概要</div>
              <div class="summary-text">{{ analysis.summary }}</div>
            </div>

            <div class="report-block">
              <div class="block-title">
                风险点
                <el-tag size="small" round>{{ analysis.riskPoints?.length || 0 }}</el-tag>
              </div>
              <div v-if="analysis.riskPoints?.length" class="risk-list">
                <div v-for="(rp, i) in analysis.riskPoints" :key="i" class="risk-card">
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
                </div>
              </div>
              <el-empty v-else description="未发现风险点" :image-size="64" />
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
        <el-icon :size="56" color="#A0AEC0"><DocumentIcon /></el-icon>
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
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
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
    background: #eef3f8;
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
  font-size: 16px;
  font-weight: 600;
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
  gap: 24px;
  padding: 10px 20px;
  background: var(--color-bg);
  font-size: 13px;
  color: var(--color-text-regular);
  border-bottom: 1px solid var(--color-border);
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
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 12px;
}
.summary-block {
  padding: 16px;
  background: #f3f8f8;
  border-left: 4px solid var(--color-accent);
  border-radius: 0 var(--radius-card) var(--radius-card) 0;
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
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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
    font-size: 16px;
    color: var(--color-primary);
  }
  p {
    font-size: 13px;
    color: var(--color-text-secondary);
  }
}
</style>
