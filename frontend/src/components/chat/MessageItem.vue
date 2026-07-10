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
</script>

<template>
  <div class="message-item" :class="message.role">
    <!-- 用户消息：右侧气泡 -->
    <div v-if="message.role === 'user'" class="user-msg">
      <div class="bubble">{{ message.content }}</div>
    </div>
    <!-- AI 消息：左侧全宽卡片 -->
    <div v-else class="assistant-msg">
      <div class="card">
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
            <div class="detail-header" @click="detailExpanded = !detailExpanded">
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
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-item {
  margin-bottom: 16px;
}
.user-msg {
  display: flex;
  justify-content: flex-end;
  .bubble {
    max-width: 70%;
    padding: 10px 14px;
    background: var(--color-primary);
    color: #fff;
    border-radius: var(--radius-card);
    word-break: break-word;
    line-height: 1.6;
    font-size: 14px;
  }
}
.assistant-msg {
  .card {
    width: 100%;
    padding: 16px;
    background: var(--color-bg-card);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-card);
    box-shadow: var(--shadow-card);
  }
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
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-bg);
  user-select: none;
  transition: background 0.15s;
  &:hover {
    background: var(--color-bg-soft);
  }
  .toggle-icon {
    font-size: 14px;
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
// 等待加载三点动画
.loading-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-text-secondary);
    animation: bounce 1.2s infinite ease-in-out;
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
  gap: 8px;
  border-top: 1px solid var(--color-border);
  padding-top: 8px;
}
@keyframes blink {
  to {
    opacity: 0;
  }
}
@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
