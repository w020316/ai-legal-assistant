<script setup lang="ts">
import { ref, computed } from 'vue'
import type { MessageVO } from '@/api'
import MarkdownRenderer from './MarkdownRenderer.vue'
import CitationCard from './CitationCard.vue'
import { CopyDocument, RefreshRight, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  message: MessageVO
  streaming?: boolean
}>()

const emit = defineEmits<{ (e: 'regenerate'): void }>()

// ===== 可视化引证芯片（v1.12.0）=====
// 解析正文中的 [N] / [Cn] 引证标记，与引用来源一一对应，点击定位高亮。
const citeMarkerPattern = /\[C?(\d+)\]/g
const activeCite = ref<number | undefined>(undefined)

function gotoCitation(idx: number, el?: HTMLElement) {
  if (el?.scrollIntoView) {
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
  // 触发 CitationCard 展开并高亮该引证项
  activeCite.value = undefined
  requestAnimationFrame(() => {
    activeCite.value = idx
  })
}

const citedIndices = computed(() => {
  const citations = props.message.citations || []
  if (citations.length === 0) return []
  const seen = new Set<number>()
  const list: { idx: number; title: string }[] = []
  const text = props.message.content || ''
  citeMarkerPattern.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = citeMarkerPattern.exec(text)) !== null) {
    const n = parseInt(m[1], 10)
    if (!Number.isNaN(n) && n >= 1 && n <= citations.length && !seen.has(n)) {
      seen.add(n)
      list.push({ idx: n, title: citations[n - 1].title || `引证 ${n}` })
    }
  }
  return list
})

// 用户反馈状态（点赞/点踩）
const feedback = ref<'like' | 'dislike' | null>(null)

function handleFeedback(type: 'like' | 'dislike') {
  feedback.value = feedback.value === type ? null : type
  if (feedback.value) {
    ElMessage.success(feedback.value === 'like' ? '感谢您的肯定' : '已记录您的反馈')
  }
}

// 复制消息内容到剪贴板
async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.message.content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 长回复折叠：检测"### 详细分析"或"## 详细分析"标题
const detailPattern = /^(#{2,3})\s*详细分析\s*$/m
const hasDetail = computed(() => detailPattern.test(props.message.content))
const splitContent = computed(() => {
  if (!hasDetail.value) return { main: props.message.content, detail: '' }
  const match = props.message.content.match(detailPattern)
  if (!match || match.index === undefined) return { main: props.message.content, detail: '' }
  const idx = match.index
  return {
    main: props.message.content.slice(0, idx).trim(),
    detail: props.message.content.slice(idx).trim(),
  }
})
const detailExpanded = ref(false)

// 格式化时间戳为 HH:MM
const formattedTime = computed(() => {
  if (!props.message.createdAt) return ''
  const d = new Date(props.message.createdAt)
  if (isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
})
</script>

<template>
  <div class="message-item" :class="message.role">
    <!-- 用户消息：右侧气泡（深墨实色，公报式） -->
    <div v-if="message.role === 'user'" class="user-msg">
      <div class="bubble">{{ message.content }}</div>
    </div>
    <!-- AI 消息：左侧全宽卡片（公报式，左侧牛血红竖线） -->
    <div v-else class="assistant-msg">
      <div class="card">
        <!-- 卡片 header：AI 助手标签 + 时间戳 -->
        <div class="card-header">
          <span class="ai-label">AI 助手</span>
          <span v-if="formattedTime" class="ai-time">{{ formattedTime }}</span>
        </div>
        <div class="card-body">
        <!-- 等待首字时显示加载动画 -->
        <div v-if="streaming && !message.content" class="loading-wrap">
          <span class="loading-dots"><i></i><i></i><i></i></span>
          <span class="loading-text">正在思考中…</span>
        </div>
        <!-- Markdown 内容（流式或无详细分析时直接渲染） -->
        <MarkdownRenderer
          v-else-if="streaming || !hasDetail"
          :content="message.content"
        />
        <!-- 完成后含详细分析：折叠渲染 -->
        <template v-else>
          <MarkdownRenderer v-if="splitContent.main" :content="splitContent.main" />
          <div class="detail-collapse">
            <div class="detail-header" role="button" tabindex="0" :aria-expanded="detailExpanded" aria-label="详细分析" @click="detailExpanded = !detailExpanded" @keydown.enter="detailExpanded = !detailExpanded">
              <el-icon class="toggle-icon">
                <ArrowDown v-if="detailExpanded" />
                <ArrowRight v-else />
              </el-icon>
              <span>详细分析</span>
            </div>
            <div v-show="detailExpanded" class="detail-body">
              <MarkdownRenderer :content="splitContent.detail" />
            </div>
          </div>
        </template>
        <!-- 流式光标 -->
        <span v-if="streaming && message.content" class="cursor">▋</span>
        <!-- 可视化引证芯片（正文 [N] 标记 → 来源定位） -->
        <div v-if="citedIndices.length" class="cite-chips">
          <span class="cite-chips-label">引证</span>
          <button
            v-for="cit in citedIndices"
            :key="cit.idx"
            class="cite-chip"
            type="button"
            :class="{ active: activeCite === cit.idx }"
            @click="gotoCitation(cit.idx, $event.currentTarget as HTMLElement)"
          >
            <span class="chip-idx">{{ cit.idx }}</span>
            <span class="chip-title">{{ cit.title }}</span>
          </button>
        </div>
        <!-- 引用来源卡片 -->
        <CitationCard
          v-if="message.citations"
          :citations="message.citations"
          :active-index="activeCite"
        />
        <!-- 操作栏（非流式且内容非空时显示） -->
        <div v-if="!streaming && message.content" class="actions">
          <el-button text size="small" :icon="CopyDocument" @click="copyContent">复制</el-button>
          <el-button text size="small" :icon="RefreshRight" @click="emit('regenerate')">重新生成</el-button>
          <span class="action-divider"></span>
          <button
            class="feedback-btn"
            :class="{ active: feedback === 'like' }"
            aria-label="点赞"
            @click="handleFeedback('like')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
          </button>
          <button
            class="feedback-btn"
            :class="{ active: feedback === 'dislike' }"
            aria-label="点踩"
            @click="handleFeedback('dislike')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17"/></svg>
          </button>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-item {
  margin-bottom: 18px;
}
// 用户消息：深墨气泡（公报式，修复原 #0B2545 硬编码）
.user-msg {
  display: flex;
  justify-content: flex-end;
  .bubble {
    max-width: 72%;
    padding: 10px 16px;
    background: linear-gradient(135deg, #7A1F2B, #5C1620);
    color: #FBF8F1;
    border-radius: 16px 16px 4px 16px;
    word-break: break-word;
    line-height: 1.6;
    font-size: 15px;
    font-family: var(--font-sans);
    box-shadow: 0 2px 8px rgba(90, 30, 20, 0.18);
    animation: slideInRight 0.35s var(--ease-out) both;
  }
}
// AI 消息：现代玻璃卡片（圆角 + 细金边 + 顶部内高光），去除旧"公报左竖线"
.assistant-msg {
  .card {
    width: 100%;
    background:
      linear-gradient(180deg, var(--glass-highlight), transparent 42%),
      var(--glass-bg);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-glass);
    box-shadow: var(--glass-shadow);
    overflow: hidden;
    animation: slideInLeft 0.35s var(--ease-out) both;
    position: relative;
    &::before {
      content: '';
      position: absolute;
      inset: 0 0 auto 0;
      height: 1px;
      background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.95) 28%, rgba(154, 107, 47, 0.4) 72%, transparent);
      border-radius: var(--radius-glass) var(--radius-glass) 0 0;
      pointer-events: none;
    }
  }
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px 10px;
  border-bottom: 1px solid var(--glass-border);
  background: rgba(255, 253, 248, 0.35);
}
.ai-label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
  letter-spacing: 0.02em;
}
.ai-time {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: var(--color-text-secondary);
}
// 可视化引证芯片
.cite-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}
.cite-chips-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  margin-right: 2px;
}
.cite-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-bg-card);
  color: var(--color-text-regular);
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-serif);
  font-size: 12px;
  max-width: 220px;
  .chip-idx {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: var(--radius-full);
    background: var(--color-accent);
    color: #FAFAF7;
    font-family: var(--font-mono);
    font-size: 10px;
    flex-shrink: 0;
  }
  .chip-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &:hover {
    border-color: var(--color-accent);
    background: var(--color-accent-light);
  }
  &.active {
    border-color: var(--color-accent);
    background: var(--color-accent-light);
    box-shadow: 0 0 0 2px rgba(122, 31, 43, 0.12);
  }
}
.card-body {
  padding: 16px 18px 18px;
}
.detail-collapse {
  margin-top: 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-glass-sm);
  overflow: hidden;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-accent);
  letter-spacing: 0.05em;
  background: var(--color-accent-light);
  user-select: none;
  transition: var(--transition-fast);
  &:hover {
    background: var(--color-accent-soft);
    color: #fff;
  }
  .toggle-icon {
    font-size: 14px;
  }
}
.detail-body {
  padding: 14px 16px;
  border-top: 1px solid var(--glass-border);
}
// 流式光标动画
.cursor {
  display: inline-block;
  margin-left: 2px;
  color: var(--color-accent);
  animation: blink 1s steps(2) infinite;
}
// 等待加载三点脉冲动画
.loading-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.loading-dots {
  display: inline-flex;
  gap: 4px;
  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-accent);
    animation: pulseDot 1.2s infinite ease-in-out;
    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}
.loading-text {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-top: 1px solid var(--color-border-light);
  padding-top: 8px;
  :deep(.el-button) {
    color: var(--color-text-secondary);
    font-family: var(--font-sans);
    &:hover {
      color: var(--color-accent);
      background: var(--color-accent-light);
    }
  }
}
.action-divider {
  width: 1px;
  height: 16px;
  background: var(--color-border);
  margin: 0 4px;
}
.feedback-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
  &:hover {
    color: var(--color-accent);
    background: var(--color-accent-light);
  }
  &.active {
    color: var(--color-accent);
    background: var(--color-accent-light);
  }
}
</style>
