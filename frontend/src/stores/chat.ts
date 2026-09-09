import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listSessions,
  createSession,
  updateSession,
  deleteSession,
  deleteSessions,
  listMessages,
  sendMessage as sendMessageApi,
  sendMessageWithImage,
  exportSession as exportSessionApi,
  type SessionVO,
  type MessageVO,
} from '@/api'
import { useUserStore } from '@/stores/user'

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

  // 批量删除会话
  async function removeSessions(ids: number[]) {
    try {
      await deleteSessions(ids)
      sessionList.value = sessionList.value.filter((s) => !ids.includes(s.id))
      if (currentSession.value && ids.includes(currentSession.value.id)) {
        currentSession.value = null
        messages.value = []
      }
    } catch {
      // 错误已处理
    }
  }

  // SSE 流式获取 AI 回复（优先模式）
  // 通过 fetch + ReadableStream 接收 Server-Sent Events，逐字显示 AI 回复
  async function fetchAssistantReplyStream(content: string): Promise<boolean> {
    if (!currentSession.value) return false
    const sessionId = currentSession.value.id
    const userStore = useUserStore()

    // v1.9.2 修复：使用唯一负 ID 替代 reactive，避免响应式双重包装
    const placeholderId = -Date.now()
    const aiMsg: MessageVO = {
      id: placeholderId,
      sessionId,
      role: 'assistant',
      content: '',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(aiMsg)
    sending.value = true
    // v1.11.0 修复 C-1：通过索引访问代理对象修改属性，确保响应式触发
    const aiMsgIdx = messages.value.length - 1

    // v1.14.0：流式内容缓冲 + 节流刷新（约 80ms 一次），避免逐 token 触发整段 Markdown 重渲染
    // —— 显著减少移动端卡顿，并让刷新时接收到更完整的语义单元（缓解内容乱码）
    // 增加 SSE 无数据超时：X 秒未收到新 chunk → 主动停止，避免卡死在"生成一半"
    let contentBuffer = ''
    let flushTimer: number | undefined = undefined
    let idleTimer: number | undefined = undefined
    let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
    const IDLE_TIMEOUT_MS = 25 * 1000 // 25 秒无新 chunk 认为卡死
    const resetIdleTimer = () => {
      if (idleTimer !== undefined) clearTimeout(idleTimer)
      idleTimer = window.setTimeout(() => {
        // 长 idle：判定卡死，停止并给出可恢复提示（不自我重置，避免死循环）
        if (reader && !reader.closed) reader.cancel()
        if (contentBuffer) flushContent()
        if (!messages.value[aiMsgIdx].content) {
          messages.value[aiMsgIdx].content = 'AI 回复超时卡住了，请点击「重新生成」重试。'
        }
        sending.value = false
        loadSessions()
      }, IDLE_TIMEOUT_MS)
    }
    const flushContent = () => {
      flushTimer = undefined
      messages.value[aiMsgIdx].content = contentBuffer
    }
    const scheduleFlush = () => {
      if (flushTimer !== undefined) return
      flushTimer = window.setTimeout(flushContent, 80)
    }

    try {
      const resp = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/sessions/${sessionId}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${userStore.token}`,
        },
        body: JSON.stringify({ content }),
      })

      if (!resp.ok || !resp.body) {
        throw new Error(`SSE 请求失败: ${resp.status}`)
      }

      const readerRef = resp.body.getReader()
      reader = readerRef
      const decoder = new TextDecoder()
      let buffer = ''
      let receivedChunk = false

      while (true) {
        if (!sending.value) {
          // 用户点击了停止
          readerRef.cancel()
          flushContent()
          if (!messages.value[aiMsgIdx].content) messages.value[aiMsgIdx].content = '已停止生成'
          break
        }
        if (currentSession.value?.id !== sessionId) {
          // 会话已切换，停止旧流
          readerRef.cancel()
          flushContent()
          break
        }

        const { done, value } = await readerRef.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // SSE 事件以双换行分隔
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''

        for (const eventStr of events) {
          const lines = eventStr.split('\n')
          let eventName = 'message'
          let eventData = ''
          for (const line of lines) {
            if (line.startsWith('event:')) eventName = line.slice(6).trim()
            else if (line.startsWith('data:')) eventData = line.slice(5).trim()
          }

          if (eventName === 'chunk') {
            receivedChunk = true
            // v1.14.0：缓冲累积，节流刷新到内容（减少整段 Markdown 高频重渲染）
            contentBuffer += eventData
            resetIdleTimer() // 有新数据即刷新看门狗
            // v1.15.0：首个 chunk 立即上屏提升「首字」感知（国内链路较长，首字越早越好），
            // 后续 chunk 仍走 80ms 节流避免高频触发整段 Markdown 重渲染卡顿
            if (!messages.value[aiMsgIdx].content) flushContent()
            else scheduleFlush()
          } else if (eventName === 'citations') {
            try {
              messages.value[aiMsgIdx].citations = JSON.parse(eventData)
            } catch {
              // JSON 解析失败时忽略
            }
          } else if (eventName === 'done') {
            sending.value = false
            flushContent() // 兜底刷新未清的空缓冲
            loadSessions()
            return true
          } else if (eventName === 'error') {
            throw new Error(eventData || 'AI 服务错误')
          }
        }
      }

      // 流正常结束但没收到 done 事件
      if (receivedChunk && contentBuffer) {
        flushContent()
        loadSessions()
        return true
      }
      // 没收到任何内容
      if (!messages.value[aiMsgIdx].content) {
        messages.value[aiMsgIdx].content = 'AI 回复为空，请稍后重试。'
      }
      return true
    } catch (e) {
      // v1.11.0 修复 H-5：SSE 失败时给用户明确提示，而非静默回退
      console.warn('[Chat] SSE 流式请求失败，将降级为轮询模式', e)
      // SSE 失败，移除占位消息，返回 false 让调用方回退到轮询模式
      const idx = messages.value.findIndex((m) => m.id === placeholderId)
      if (idx >= 0) messages.value.splice(idx, 1)
      return false
    } finally {
      if (flushTimer !== undefined) clearTimeout(flushTimer)
      if (idleTimer !== undefined) clearTimeout(idleTimer)
      sending.value = false
    }
  }

  // 异步获取 AI 回复（轮询模式，SSE 失败时的降级方案）
  // POST 立即返回用户消息 ID，后台异步调用 AI，前端轮询消息列表获取回复
  async function fetchAssistantReply(content: string) {
    // 优先尝试 SSE 流式
    const sseOk = await fetchAssistantReplyStream(content)
    if (sseOk) return

    // SSE 失败，降级为轮询模式
    if (!currentSession.value) return
    const sessionId = currentSession.value.id
    const placeholderId = -Date.now()
    const aiMsg: MessageVO = {
      id: placeholderId,
      sessionId,
      role: 'assistant',
      content: '',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(aiMsg)
    sending.value = true
    // v1.11.0 修复 C-1：通过索引访问代理对象修改属性
    const aiMsgIdx = messages.value.length - 1
    try {
      // 发送消息，后端立即返回用户消息 ID
      await sendMessageApi(sessionId, content)
      // 轮询消息列表，等待 AI 回复出现
      const maxAttempts = 60 // 最多轮询 60 次（约 120 秒）
      const interval = 2000 // 每 2 秒轮询一次
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((resolve) => setTimeout(resolve, interval))
        if (!sending.value) return // 用户点击了停止
        if (currentSession.value?.id !== sessionId) return // 会话已切换，停止旧轮询
        const res = await listMessages(sessionId)
        const msgs = res.data
        // 找到最后一条 assistant 消息
        const lastAssistant = [...msgs].reverse().find((m) => m.role === 'assistant')
        if (lastAssistant && lastAssistant.content) {
          // v1.11.0 修复 C-1：通过代理索引修改，触发响应式
          messages.value[aiMsgIdx].id = lastAssistant.id
          messages.value[aiMsgIdx].content = lastAssistant.content
          // AI 回复成功后刷新会话列表（获取自动命名的标题）
          loadSessions()
          messages.value[aiMsgIdx].citations = lastAssistant.citations
          messages.value[aiMsgIdx].tokens = lastAssistant.tokens
          messages.value[aiMsgIdx].createdAt = lastAssistant.createdAt
          break
        }
      }
      // 超时仍未收到回复
      if (!messages.value[aiMsgIdx].content) {
        messages.value[aiMsgIdx].content = 'AI 回复超时，请稍后重试。'
      }
    } catch {
      ElMessage.error('发送失败，请重试')
      // AI 消息为空时移除占位消息
      if (!messages.value[aiMsgIdx].content) {
        const idx = messages.value.findIndex((m) => m.id === placeholderId)
        if (idx >= 0) messages.value.splice(idx, 1)
      }
    } finally {
      sending.value = false
    }
  }

  // 发送图片消息
  async function sendImageMessage(file: File) {
    if (!currentSession.value || sending.value) return
    // 添加占位消息
    const userPlaceholderId = -Date.now()
    const userMsg: MessageVO = {
      id: userPlaceholderId,
      sessionId: currentSession.value.id,
      role: 'user',
      content: '📷 [图片识别中...]',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMsg)
    const aiPlaceholderId = -Date.now() - 1
    const aiMsg: MessageVO = {
      id: aiPlaceholderId,
      sessionId: currentSession.value.id,
      role: 'assistant',
      content: '',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(aiMsg)
    sending.value = true
    // v1.11.0 修复 C-1：通过索引访问代理对象修改属性
    const userMsgIdx = messages.value.length - 2
    const aiMsgIdx = messages.value.length - 1
    try {
      const sessionId = currentSession.value.id
      await sendMessageWithImage(sessionId, file)
      // 轮询消息列表
      const maxAttempts = 60
      const interval = 2000
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((resolve) => setTimeout(resolve, interval))
        if (!sending.value) return
        if (currentSession.value?.id !== sessionId) return // 会话已切换，停止旧轮询
        const res = await listMessages(sessionId)
        const msgs = res.data
        // 更新用户消息（识别后的问题）
        const lastUser = [...msgs].reverse().find((m) => m.role === 'user')
        if (lastUser && !lastUser.content.includes('[图片消息]')) {
          messages.value[userMsgIdx].content = lastUser.content
        }
        // 查找 AI 回复
        const lastAssistant = [...msgs].reverse().find((m) => m.role === 'assistant')
        if (lastAssistant && lastAssistant.content) {
          messages.value[aiMsgIdx].id = lastAssistant.id
          messages.value[aiMsgIdx].content = lastAssistant.content
          messages.value[aiMsgIdx].citations = lastAssistant.citations
          messages.value[aiMsgIdx].tokens = lastAssistant.tokens
          messages.value[aiMsgIdx].createdAt = lastAssistant.createdAt
          break
        }
      }
      if (!messages.value[aiMsgIdx].content) {
        messages.value[aiMsgIdx].content = '图片识别超时，请稍后重试。'
      }
      // 刷新会话列表（获取自动命名的标题）
      loadSessions()
    } catch {
      ElMessage.error('图片上传失败，请重试')
      if (!messages.value[aiMsgIdx].content) {
        const idx = messages.value.findIndex((m) => m.id === aiPlaceholderId)
        if (idx >= 0) messages.value.splice(idx, 1)
      }
      const uIdx = messages.value.findIndex((m) => m.id === userPlaceholderId)
      if (uIdx >= 0 && messages.value[uIdx].content.includes('[图片')) {
        messages.value.splice(uIdx, 1)
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
    // 检查最后一条消息是否为空内容的 assistant 占位消息
    const msgs = messages.value
    const last = msgs[msgs.length - 1]
    if (last && last.role === 'assistant' && !last.content) {
      last.content = '已停止生成'
    }
  }

  // 导出会话为 Markdown（v1.9.1 新增）
  async function exportSession(sessionId: number): Promise<string | null> {
    try {
      const res = await exportSessionApi(sessionId)
      return res.data
    } catch {
      return null
    }
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
    removeSessions,
    sendMessage,
    sendImageMessage,
    regenerate,
    stopGenerating,
    exportSession,
  }
})
