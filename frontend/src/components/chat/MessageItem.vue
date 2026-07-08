<script setup lang="ts">
import type { MessageVO } from '@/api'
import MarkdownRenderer from './MarkdownRenderer.vue'
import CitationCard from './CitationCard.vue'
import { CopyDocument, RefreshRight } from '@element-plus/icons-vue'
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
        <!-- Markdown 内容 -->
        <MarkdownRenderer v-else :content="message.content" />
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
