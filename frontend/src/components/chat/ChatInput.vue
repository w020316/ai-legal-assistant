<script setup lang="ts">
import { ref, computed } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'

const props = defineProps<{
  sending: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', content: string): void
  (e: 'stop'): void
}>()

const input = ref('')
const maxLen = 2000

// 是否可发送
const canSend = computed(() => input.value.trim().length > 0 && !props.sending && !props.disabled)

// 发送消息
function handleSend() {
  if (!canSend.value) return
  const content = input.value.trim()
  emit('send', content)
  input.value = ''
}

// 键盘事件：Enter 发送，Shift+Enter 换行，输入法 composing 时不触发
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-input">
    <div class="input-wrap">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        :maxlength="maxLen"
        resize="none"
        :placeholder="disabled ? '请先选择或新建会话' : '输入法律问题，Enter 发送，Shift+Enter 换行'"
        :disabled="disabled"
        @keydown="handleKeydown"
      />
    </div>
    <div class="toolbar">
      <span class="counter">{{ input.length }} / {{ maxLen }}</span>
      <div class="btn-group">
        <el-button
          v-if="!sending"
          type="primary"
          :icon="Promotion"
          :disabled="!canSend"
          @click="handleSend"
        >
          发送
        </el-button>
        <el-button v-else type="danger" :icon="VideoPause" @click="emit('stop')">停止生成</el-button>
      </div>
    </div>
    <div class="disclaimer">本回答由 AI 生成，仅供参考，不构成法律意见。</div>
  </div>
</template>

<style scoped lang="scss">
.chat-input {
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-card);
  padding: 12px 24px 8px;
}
.input-wrap {
  :deep(.el-textarea__inner) {
    border-radius: var(--radius-card);
    font-family: var(--font-family);
    line-height: 1.6;
  }
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.counter {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.btn-group {
  display: flex;
  gap: 8px;
}
.disclaimer {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}
</style>
