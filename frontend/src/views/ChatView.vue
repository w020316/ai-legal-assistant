<script setup lang="ts">
import { onMounted, ref, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { SessionVO } from '@/api'
import SessionList from '@/components/chat/SessionList.vue'
import MessageItem from '@/components/chat/MessageItem.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { ChatDotRound } from '@element-plus/icons-vue'

const chatStore = useChatStore()
const scrollRef = ref<HTMLElement>()

// 滚动到底部
async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

// 发送消息
async function handleSend(content: string) {
  await chatStore.sendMessage(content)
  scrollToBottom()
}

// 选择会话
async function handleSelect(session: SessionVO) {
  await chatStore.selectSession(session)
  scrollToBottom()
}

// 新建会话
async function handleCreate() {
  await chatStore.createNewSession()
  scrollToBottom()
}

// 重新生成
async function handleRegenerate() {
  await chatStore.regenerate()
  scrollToBottom()
}

// 消息列表变化时自动滚动
watch(
  () => chatStore.messages.length,
  () => scrollToBottom(),
)

// 流式内容变化时滚动（监听最后一条消息内容）
watch(
  () => chatStore.messages[chatStore.messages.length - 1]?.content,
  () => scrollToBottom(),
)

onMounted(() => {
  chatStore.loadSessions()
})
</script>

<template>
  <div class="chat-view">
    <!-- 左侧会话列表 -->
    <div class="left-panel">
      <SessionList
        :sessions="chatStore.sessionList"
        :current-id="chatStore.currentSession?.id"
        @select="handleSelect"
        @create="handleCreate"
        @rename="chatStore.renameSession"
        @toggle-star="chatStore.toggleStar"
        @delete="chatStore.removeSession"
      />
    </div>
    <!-- 右侧消息区 -->
    <div class="right-panel">
      <!-- 消息流 -->
      <div ref="scrollRef" class="message-stream">
        <!-- 空状态引导 -->
        <div v-if="!chatStore.hasSession" class="empty-state">
          <el-icon :size="64" color="#1E3A5F"><ChatDotRound /></el-icon>
          <h2>AI 法律助手</h2>
          <p>面向法律从业者的智能问答平台</p>
          <p class="hint">请选择左侧会话，或点击「新建」开始对话</p>
        </div>
        <!-- 消息列表 -->
        <div v-else class="msg-list">
          <MessageItem
            v-for="(msg, i) in chatStore.messages"
            :key="i"
            :message="msg"
            :streaming="chatStore.sending && i === chatStore.messages.length - 1 && msg.role === 'assistant'"
            @regenerate="handleRegenerate"
          />
        </div>
      </div>
      <!-- 底部输入区 -->
      <ChatInput
        :sending="chatStore.sending"
        :disabled="!chatStore.hasSession"
        @send="handleSend"
        @stop="chatStore.stopGenerating"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-view {
  display: flex;
  height: 100%;
  background: var(--color-bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  border: 1px solid var(--color-border);
}
.left-panel {
  width: 260px;
  border-right: 1px solid var(--color-border);
  flex-shrink: 0;
  background: var(--color-bg-card);
}
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.message-stream {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}
.msg-list {
  max-width: 900px;
  margin: 0 auto;
}
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-regular);
  h2 {
    margin: 16px 0 8px;
    font-size: 22px;
    color: var(--color-primary);
  }
  p {
    font-size: 14px;
    margin: 4px 0;
  }
  .hint {
    margin-top: 12px;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}
</style>
