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
        <span v-if="streaming && !message.content" class="loading-dots">
          <i></i><i></i><i></i>
        </span>
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
        <!-- 引用来源卡片 -->
        <CitationCard v-if="message.citations" :citations="message.citations" />
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
    max-width: 70%;
    padding: 10px 14px;
    background: var(--color-primary);
    color: #FBF8F1;
    border-radius: var(--radius-sm) var(--radius-card) var(--radius-card) var(--radius-card);
    word-break: break-word;
    line-height: 1.6;
    font-size: 14px;
    font-family: var(--font-serif);
    box-shadow: var(--shadow-card);
    animation: slideInRight 0.35s var(--ease-out) both;
  }
}
// AI 消息：公报式卡片（左侧牛血红竖线 + 象牙纸底）
.assistant-msg {
  .card {
    width: 100%;
    background-color: var(--color-bg-card);
    border: 1px solid var(--color-border);
    // 左侧牛血红竖线（公报式引文标记）
    border-left: 3px solid var(--color-accent);
    border-radius: var(--radius-card);
    box-shadow: var(--shadow-card);
    overflow: hidden;
    animation: slideInLeft 0.35s var(--ease-out) both;
  }
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 8px;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-soft);
}
.ai-label {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 600;
  font-style: italic;
  color: var(--color-accent);
  letter-spacing: 0.01em;
}
.ai-time {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.card-body {
  padding: 14px 16px 16px;
}
.detail-collapse {
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  overflow: hidden;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: var(--color-bg-soft);
  user-select: none;
  transition: var(--transition-fast);
  &:hover {
    background: var(--color-accent-light);
  }
  .toggle-icon {
    font-size: 14px;
    color: var(--color-accent);
  }
}
.detail-body {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
}
// 流式光标动画
.cursor {
  display: inline-block;
  margin-left: 2px;
  color: var(--color-accent);
  animation: blink 1s steps(2) infinite;
}
// 等待加载三点脉冲动画
.loading-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
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
