import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listSessions,
  createSession,
  updateSession,
  deleteSession,
  listMessages,
  sendMessage as sendMessageApi,
  type SessionVO,
  type MessageVO,
} from '@/api'

export const useChatStore = defineStore('chat', () => {
  // 会话列表
  const sessionList = ref<SessionVO[]>([])
  // 当前选中会话
  const currentSession = ref<SessionVO | null>(null)
  // 当前会话消息列表
  const messages = ref<MessageVO[]>([])
  // 是否正在发送（等待 AI 回复中）
  const sending = ref(false)

  // 是否有选中会话
  const hasSession = computed(() => !!currentSession.value)

  // 加载会话列表
  async function loadSessions() {
    try {
      const res = await listSessions()
      // 后端返回纯数组，兼容分页结构
      const data = res.data as any
      sessionList.value = Array.isArray(data) ? data : (data.records ?? [])
    } catch {
      // 错误已由请求拦截器统一提示
    }
  }

  // 选择会话并加载消息历史
  async function selectSession(session: SessionVO) {
    currentSession.value = session
    try {
      const res = await listMessages(session.id)
      messages.value = res.data
    } catch {
      messages.value = []
    }
  }

  // 新建会话
  async function createNewSession(title?: string) {
    try {
      const res = await createSession(title ? { title } : undefined)
      sessionList.value.unshift(res.data)
      await selectSession(res.data)
      return res.data
    } catch {
      return null
    }
  }

  // 重命名会话
  async function renameSession(id: number, title: string) {
    try {
      const res = await updateSession(id, { title })
      const idx = sessionList.value.findIndex((s) => s.id === id)
      if (idx >= 0) sessionList.value[idx] = res.data
      if (currentSession.value?.id === id) currentSession.value = res.data
    } catch {
      // 错误已处理
    }
  }

  // 收藏/取消收藏
  async function toggleStar(session: SessionVO) {
    try {
      const res = await updateSession(session.id, { starred: !session.starred })
      const idx = sessionList.value.findIndex((s) => s.id === session.id)
      if (idx >= 0) sessionList.value[idx] = res.data
      if (currentSession.value?.id === session.id) currentSession.value = res.data
    } catch {
      // 错误已处理
    }
  }

  // 删除会话
  async function removeSession(id: number) {
    try {
      await deleteSession(id)
      sessionList.value = sessionList.value.filter((s) => s.id !== id)
      if (currentSession.value?.id === id) {
        currentSession.value = null
        messages.value = []
      }
    } catch {
      // 错误已处理
    }
  }

  // 异步获取 AI 回复（轮询模式）
  // POST 立即返回用户消息 ID，后台异步调用 AI，前端轮询消息列表获取回复
  async function fetchAssistantReply(content: string) {
    if (!currentSession.value) return
    const sessionId = currentSession.value.id
    const aiMsg = reactive<MessageVO>({
      id: 0,
      sessionId,
      role: 'assistant',
      content: '',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    })
    messages.value.push(aiMsg)
    sending.value = true
    try {
      // 发送消息，后端立即返回用户消息 ID
      await sendMessageApi(sessionId, content)
      // 轮询消息列表，等待 AI 回复出现
      const maxAttempts = 60 // 最多轮询 60 次（约 120 秒）
      const interval = 2000 // 每 2 秒轮询一次
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((resolve) => setTimeout(resolve, interval))
        if (!sending.value) return // 用户点击了停止
        const res = await listMessages(sessionId)
        const msgs = res.data
        // 找到最后一条 assistant 消息
        const lastAssistant = [...msgs].reverse().find((m) => m.role === 'assistant')
        if (lastAssistant && lastAssistant.content) {
          aiMsg.id = lastAssistant.id
          aiMsg.content = lastAssistant.content
          aiMsg.citations = lastAssistant.citations
          aiMsg.tokens = lastAssistant.tokens
          aiMsg.createdAt = lastAssistant.createdAt
          break
        }
      }
      // 超时仍未收到回复
      if (!aiMsg.content) {
        aiMsg.content = 'AI 回复超时，请稍后重试。'
      }
    } catch (e) {
      ElMessage.error('发送失败，请重试')
      // AI 消息为空时移除占位消息
      if (!aiMsg.content) {
        const idx = messages.value.lastIndexOf(aiMsg)
        if (idx >= 0) messages.value.splice(idx, 1)
      }
    } finally {
      sending.value = false
    }
  }

  // 发送消息（插入用户消息 + 同步请求 AI 回复）
  async function sendMessage(content: string) {
    if (!currentSession.value || sending.value) return
    const userMsg: MessageVO = {
      id: 0,
      sessionId: currentSession.value.id,
      role: 'user',
      content,
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMsg)
    await fetchAssistantReply(content)
  }

  // 重新生成最后一条 AI 回复
  async function regenerate() {
    if (!currentSession.value || sending.value) return
    const msgs = messages.value
    // 找最后一条用户消息
    let lastUserIdx = -1
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'user') {
        lastUserIdx = i
        break
      }
    }
    if (lastUserIdx < 0) return
    const content = msgs[lastUserIdx].content
    // 移除最后的 AI 消息（若存在）
    if (msgs.length > lastUserIdx + 1 && msgs[msgs.length - 1].role === 'assistant') {
      msgs.splice(msgs.length - 1, 1)
    }
    await fetchAssistantReply(content)
  }

  // 停止生成（同步模式下仅重置状态，无法中断已发出的后端请求）
  function stopGenerating() {
    sending.value = false
  }

  return {
    sessionList,
    currentSession,
    messages,
    sending,
    hasSession,
    loadSessions,
    selectSession,
    createNewSession,
    renameSession,
    toggleStar,
    removeSession,
    sendMessage,
    regenerate,
    stopGenerating,
  }
})
