<script setup lang="ts">
import { onMounted, ref, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import type { SessionVO } from '@/api'
import SessionList from '@/components/chat/SessionList.vue'
import MessageItem from '@/components/chat/MessageItem.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { Reading } from '@element-plus/icons-vue'

const chatStore = useChatStore()
const scrollRef = ref<HTMLElement>()

// 滚动到底部
async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

// 导出会话为 Markdown 文件（v1.9.1 新增）
async function handleExport(sessionId: number) {
  const content = await chatStore.exportSession(sessionId)
  if (content) {
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.download = `linzAI会话导出_${new Date().toISOString().slice(0, 10)}.md`
    link.href = url
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('会话已导出')
  } else {
    ElMessage.error('导出失败，请稍后重试')
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

// 批量删除会话
function handleBatchDelete(ids: number[]) {
  chatStore.removeSessions(ids)
}

// 上传图片识别
function handleUploadImage(file: File) {
  chatStore.sendImageMessage(file)
}

// 推荐问题
const suggestions = [
  '什么是诉讼时效？',
  '劳动合同解除的法定情形有哪些？',
  '民间借贷利率的上限是多少？',
  '如何认定夫妻共同财产？',
]

function handleSuggestion(text: string) {
  handleSend(text)
}

// 阿拉伯数字转罗马数字（公报章节编号风）
function toRoman(num: number): string {
  const map: [number, string][] = [
    [10, 'X'],
    [9, 'IX'],
    [5, 'V'],
    [4, 'IV'],
    [1, 'I'],
  ]
  let result = ''
  let n = num
  for (const [v, s] of map) {
    while (n >= v) {
      result += s
      n -= v
    }
  }
  return result
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
        @batch-delete="handleBatchDelete"
        @export="handleExport"
      />
    </div>
    <!-- 右侧消息区 -->
    <div class="right-panel">
      <!-- 消息流 -->
      <div ref="scrollRef" class="message-stream">
        <!-- 空状态引导：公报封面式 -->
        <div v-if="!chatStore.hasSession" class="empty-state">
          <span class="empty-eyebrow">EST. MMXXVI · INQUIRY</span>
          <span class="empty-icon-badge">
            <el-icon :size="36" color="var(--color-accent)"><Reading /></el-icon>
          </span>
          <h2>开始您的法律问答</h2>
          <p class="empty-sub">面向法律从业者的智能问答平台，基于权威语料检索增强</p>
          <div class="gazette-divider" aria-hidden="true">❦</div>
          <p class="hint">点击下方按钮，开始您的第一次咨询</p>
          <el-button type="primary" size="large" class="create-btn" @click="handleCreate">
            新 建 对 话
          </el-button>
        </div>
        <!-- 消息列表 -->
        <div v-else class="msg-list">
          <!-- 空会话推荐问题：公报目录式 -->
          <div v-if="chatStore.messages.length === 0" class="suggestions">
            <div class="suggestions-eyebrow">TABLE OF INQUIRIES</div>
            <div class="suggestions-title">您可以直接提问，或试试以下问题</div>
            <div class="suggestion-grid">
              <div
                v-for="(s, i) in suggestions"
                :key="s"
                class="suggestion-card"
                @click="handleSuggestion(s)"
              >
                <span class="suggestion-index">{{ toRoman(i + 1) }}</span>
                <span class="suggestion-text">{{ s }}</span>
              </div>
            </div>
          </div>
          <!-- 消息列表 -->
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
        image-enabled
        @send="handleSend"
        @stop="chatStore.stopGenerating"
        @upload-image="handleUploadImage"
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
  box-shadow: var(--shadow-card);
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
  background: var(--color-bg);
}
.message-stream {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}
.msg-list {
  max-width: 800px;
  margin: 0 auto;
}
.suggestions {
  max-width: 720px;
  margin: 48px auto;
  text-align: center;
}
// 公报目录式 eyebrow：小型大写字母 + 古铜金 + 字距加宽
.suggestions-eyebrow {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-gilt);
  letter-spacing: 0.32em;
  text-transform: uppercase;
  margin-bottom: 8px;
}
.suggestions-title {
  font-family: var(--font-display);
  font-size: 22px;
  font-style: italic;
  color: var(--color-primary);
  margin-bottom: 24px;
  letter-spacing: -0.01em;
  &::after {
    content: '';
    display: block;
    width: 48px;
    height: 1px;
    background: var(--color-accent);
    margin: 14px auto 0;
  }
}
// 2 列锯齿网格（非等宽）：奇偶列宽度不等 + 偶数项下沉形成锯齿节奏
.suggestion-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 12px 16px;
  text-align: left;
}
.suggestion-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  // 预留 3px 左边框，hover 时染色为牛血红竖线（公报引文标记）
  border-left: 3px solid transparent;
  border-radius: var(--radius-card);
  background: var(--color-bg-card);
  cursor: pointer;
  font-family: var(--font-serif);
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.6;
  transition: var(--transition-base);
  // staggered 入场动画（每个卡片延迟 0.05s）
  animation: fadeInUp 0.5s var(--ease-out) both;
  &:nth-child(1) { animation-delay: 0.05s; }
  &:nth-child(2) { animation-delay: 0.1s; }
  &:nth-child(3) { animation-delay: 0.15s; }
  &:nth-child(4) { animation-delay: 0.2s; }
  // 偶数项下沉，形成锯齿错落
  &:nth-child(even) {
    margin-top: 20px;
  }
  &:hover {
    border-color: var(--color-border);
    // 左侧出现 3px 牛血红竖线（公报引文式）
    border-left: 3px solid var(--color-accent);
    color: var(--color-primary);
    box-shadow: var(--shadow-hover);
    transform: translateY(-1px);
    .suggestion-index {
      color: var(--color-accent);
      opacity: 1;
      transform: scale(1.05);
    }
  }
}
// 罗马数字编号：公报章节式，衬线展示字体 + 古铜金
.suggestion-index {
  flex-shrink: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  font-style: italic;
  color: var(--color-gilt);
  letter-spacing: 0.02em;
  line-height: 1.3;
  opacity: 0.75;
  min-width: 28px;
  transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out), color 0.2s var(--ease-out);
}
.suggestion-text {
  flex: 1;
  font-family: var(--font-serif);
}
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-regular);
  text-align: center;
  user-select: none;
  // 公报封面式 eyebrow：小型大写字母 + 古铜金 + 字距加宽
  .empty-eyebrow {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 600;
    color: var(--color-gilt);
    letter-spacing: 0.4em;
    text-transform: uppercase;
    margin-bottom: 20px;
    animation: fadeInUp 0.5s var(--ease-out) both;
  }
  .empty-icon-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    border-radius: var(--radius-full);
    background: rgba(122, 31, 43, 0.08);
    border: 1px solid rgba(122, 31, 43, 0.18);
    // 脉冲呼吸动画（LottieFiles 风格）
    animation: breathe 3s ease-in-out infinite;
  }
  h2 {
    margin: 24px 0 8px;
    font-family: var(--font-display);
    font-size: 38px;
    font-weight: 600;
    color: var(--color-primary);
    letter-spacing: -0.015em;
    animation: fadeInUp 0.5s var(--ease-out) both;
    animation-delay: 0.1s;
  }
  .empty-sub {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 15px;
    margin: 4px 0;
    max-width: 52ch;
    color: var(--color-text-secondary);
    animation: fadeInUp 0.5s var(--ease-out) both;
    animation-delay: 0.15s;
  }
  // 公报式花纹分隔
  .gazette-divider {
    margin: 18px 0 6px;
    font-size: 18px;
    color: var(--color-gilt);
    opacity: 0.7;
    animation: fadeInUp 0.5s var(--ease-out) both;
    animation-delay: 0.2s;
  }
  p {
    font-size: 14px;
    margin: 4px 0;
    max-width: 48ch;
    animation: fadeInUp 0.5s var(--ease-out) both;
    animation-delay: 0.15s;
  }
  .hint {
    margin-top: 8px;
    color: var(--color-text-secondary);
    font-size: 13px;
    font-family: var(--font-serif);
    font-style: italic;
  }
  .create-btn {
    margin-top: 24px;
    letter-spacing: 0.18em;
    font-family: var(--font-display);
    font-weight: 500;
    // 微妙悬浮动画
    animation: floatBtn 3.5s ease-in-out infinite;
  }
}
// 按钮微妙悬浮（上下漂浮 2px）
@keyframes floatBtn {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .chat-view {
    border-radius: 0;
    border: none;
  }
  .left-panel {
    width: 72px;
  }
  .message-stream {
    padding: 16px 14px;
  }
  /* 消息宽度改为 100% */
  .msg-list {
    max-width: 100%;
  }
  .suggestions {
    max-width: 100%;
    margin: 24px auto;
  }
  /* 推荐问题网格改为单列 */
  .suggestion-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .suggestion-card {
    &:nth-child(even) {
      margin-top: 0;
    }
  }
  .empty-state {
    h2 {
      font-size: 20px;
    }
    p {
      font-size: 13px;
    }
  }
}
</style>
