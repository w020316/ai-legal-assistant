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

// v1.18(B-05)：发送幂等键。同一逻辑消息的 SSE 首发与轮询重试复用同一值，
// 后端据此去重，消除"SSE 失败重试导致用户消息重复落库"。
function newClientMsgId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {
    // 非安全上下文等场景回退到时间戳+随机串
  }
  return `c-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

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
  async function fetchAssistantReplyStream(content: string, clientMsgId: string): Promise<boolean> {
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
    // v1.18 修复(V1-06)：持有数组内"响应式代理"对象引用而非下标——流式期间切换会话会整体替换
    // messages 数组，旧下标会指向新会话消息（串写/或越界 TypeError 令 UI 永久卡"生成中"）。
    // 数组元素即该对象的响应式代理，通过引用写入同样触发响应，且只作用于本会话的占位对象。
    const aiMsgView = messages.value[messages.value.length - 1]

    // v1.14.0：流式内容缓冲 + 节流刷新（约 80ms 一次），避免逐 token 触发整段 Markdown 重渲染
    // —— 显著减少移动端卡顿，并让刷新时接收到更完整的语义单元（缓解内容乱码）
    // 增加 SSE 无数据超时：X 秒未收到新 chunk → 主动停止，避免卡死在"生成一半"
    let contentBuffer = ''
    let flushTimer: number | undefined = undefined
    let idleTimer: number | undefined = undefined
    let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
    const IDLE_TIMEOUT_MS = 25 * 1000 // 25 秒无新 chunk 认为卡死
    let stalled = false // 看门狗判定卡死，用于打断后续"正常完成"成功路径
    // v1.18 修复(V1-07)：后端已受理(HTTP 200)即视为"已提交"——用户消息已/将落库，
    // 此时任何中断都不允许降级重发同一内容（否则消息成对重复落库）
    let committed = false
    const resetIdleTimer = () => {
      if (idleTimer !== undefined) clearTimeout(idleTimer)
      idleTimer = window.setTimeout(() => {
        // 长 idle：判定卡死，停止并给出可恢复提示（不自我重置，避免死循环）
        stalled = true
        if (reader && !reader.closed) reader.cancel()
        if (contentBuffer) flushContent()
        if (!aiMsgView.content) {
          aiMsgView.content = 'AI 回复超时卡住了，请点击「重新生成」重试。'
        }
        sending.value = false
        loadSessions()
      }, IDLE_TIMEOUT_MS)
    }
    const flushContent = () => {
      flushTimer = undefined
      aiMsgView.content = contentBuffer
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
        body: JSON.stringify({ content, clientMsgId }),
      })

      if (!resp.ok || !resp.body) {
        throw new Error(`SSE 请求失败: ${resp.status}`)
      }
      committed = true

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
          if (!aiMsgView.content) aiMsgView.content = '已停止生成'
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
        // v1.17：仅折叠完整的 CRLF（兼容个别代理/CDN 输出的 \r\n 帧）。
        // 不单独处理裸 \r：若 \r 与 \n 恰好被拆到两次 read，仅折叠 \r\n 可在下次拼接后正确合并，避免误判事件边界。
        buffer = buffer.replace(/\r\n/g, '\n')
        // SSE 事件以双换行分隔
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''

        for (const eventStr of events) {
          const lines = eventStr.split('\n')
          let eventName = 'message'
          const dataLines: string[] = []
          for (const line of lines) {
            // SSE 规范：data 可多行，需以 \n 拼接；冒号后仅去掉一个可选前导空格，其余原样保留
            if (line.startsWith('event:')) eventName = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
          }
          const eventData = dataLines.join('\n')

          if (eventName === 'chunk') {
            receivedChunk = true
            // v1.14.0：缓冲累积，节流刷新到内容（减少整段 Markdown 高频重渲染）
            contentBuffer += eventData
            resetIdleTimer() // 有新数据即刷新看门狗
            // v1.15.0：首个 chunk 立即上屏提升「首字」感知（国内链路较长，首字越早越好），
            // 后续 chunk 仍走 80ms 节流避免高频触发整段 Markdown 重渲染卡顿
            if (!aiMsgView.content) flushContent()
            else scheduleFlush()
          } else if (eventName === 'citations') {
            try {
              aiMsgView.citations = JSON.parse(eventData)
            } catch {
              // JSON 解析失败时忽略
            }
            // v1.18(P3)：citations 后上游可能挂死（如 Agnes 无空闲超时）——从此时也启动看门狗，
            // 不再等到首个 chunk，25s 无后续数据即恢复 UI
            resetIdleTimer()
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

      // 看门狗判定卡死：不再走"正常完成"路径，避免二次 flush 覆盖卡死提示；
      // 半截内容已在 idle 回调中上屏，这里补足空内容时的提示后退出
      if (stalled) {
        if (!aiMsgView.content) {
          aiMsgView.content = 'AI 回复超时卡住了，请点击「重新生成」重试。'
        }
        loadSessions()
        return true
      }

      // 流正常结束但没收到 done 事件
      if (receivedChunk && contentBuffer) {
        flushContent()
        loadSessions()
        return true
      }
      // 没收到任何内容
      if (!aiMsgView.content) {
        aiMsgView.content = 'AI 回复为空，请稍后重试。'
      }
      return true
    } catch (e) {
      // v1.11.0 修复 H-5：SSE 失败时给用户明确提示，而非静默回退
      console.warn('[Chat] SSE 流式请求失败，将降级为轮询模式', e)
      // v1.18 修复(V1-07)：后端已受理的流中断（含后端 error 事件/网络断）——
      // 用户消息已落库，重发同一内容会造成消息成对重复落库。就地终态：保留已收内容/给出提示。
      if (committed) {
        if (contentBuffer) flushContent()
        if (!aiMsgView.content) {
          // v1.18 修复(F1)：写入响应式代理 aiMsgView（此前误写原始对象 aiMsg，
          // 绕过 reactive set trap 不触发渲染 → 错误文案永不显示，用户只见永久空白气泡）
          aiMsgView.content =
            e instanceof Error && e.message && !e.message.startsWith('SSE 请求失败')
              ? e.message
              : 'AI 服务暂时不可用，请稍后重试。'
        }
        loadSessions()
        return true
      }
      // 未受理（连接/鉴权层失败）：后端未保存用户消息，移除占位并降级轮询（重发安全）
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
    // v1.18(B-05)：同一逻辑消息的幂等键，SSE 首发与降级轮询复用同一值，后端据此去重
    const clientMsgId = newClientMsgId()
    // 优先尝试 SSE 流式
    const sseOk = await fetchAssistantReplyStream(content, clientMsgId)
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
    // v1.18 修复(F3)：持有数组内"响应式代理"元素引用而非下标——会话切换会整体替换
    // messages 数组，旧下标会指向新会话消息（串写/越界）。与 SSE 路径(V1-06)同口径。
    const aiMsgView = messages.value[messages.value.length - 1]
    // 元素是否仍属于当前会话的消息数组（切换/删除会话后为 false，停止写入）
    const isCurrent = () =>
      currentSession.value?.id === sessionId && messages.value.includes(aiMsgView)
    try {
      // 发送消息，后端立即返回用户消息 ID
      await sendMessageApi(sessionId, content, clientMsgId)
      // 轮询消息列表，等待 AI 回复出现
      const maxAttempts = 90 // 最多轮询 90 次（约 180 秒，大于后端 150s 外层超时，确保由后端裁决）
      const interval = 2000 // 每 2 秒轮询一次
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((resolve) => setTimeout(resolve, interval))
        if (!sending.value) return // 用户点击了停止
        if (!isCurrent()) return // 会话已切换/元素已移除，停止旧轮询
        const res = await listMessages(sessionId)
        // 轮询期间可能已切换会话，写入前再次校验，避免串写到新会话
        if (!isCurrent()) return
        const msgs = res.data
        // 找到最后一条 assistant 消息
        const lastAssistant = [...msgs].reverse().find((m) => m.role === 'assistant')
        if (lastAssistant && lastAssistant.content) {
          // v1.18(F3)：通过持有的响应式代理引用写入，仅作用于本会话占位对象
          aiMsgView.id = lastAssistant.id
          aiMsgView.content = lastAssistant.content
          // AI 回复成功后刷新会话列表（获取自动命名的标题）
          loadSessions()
          aiMsgView.citations = lastAssistant.citations
          aiMsgView.tokens = lastAssistant.tokens
          aiMsgView.createdAt = lastAssistant.createdAt
          break
        }
      }
      // 超时仍未收到回复
      if (isCurrent() && !aiMsgView.content) {
        aiMsgView.content = 'AI 回复超时，请稍后重试。'
      }
    } catch {
      ElMessage.error('发送失败，请重试')
      // AI 消息为空时移除占位消息
      if (isCurrent() && !aiMsgView.content) {
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
    const sessionId = currentSession.value.id
    // 添加占位消息
    const userPlaceholderId = -Date.now()
    const userMsg: MessageVO = {
      id: userPlaceholderId,
      sessionId,
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
      sessionId,
      role: 'assistant',
      content: '',
      citations: null,
      tokens: null,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(aiMsg)
    sending.value = true
    // v1.18 修复(F3)：持有响应式代理元素引用而非下标（同 fetchAssistantReply 口径）
    const userMsgView = messages.value[messages.value.length - 2]
    const aiMsgView = messages.value[messages.value.length - 1]
    const isCurrent = () =>
      currentSession.value?.id === sessionId && messages.value.includes(aiMsgView)
    try {
      // v1.18(B-05)：图片消息幂等键，后端据此对重试去重
      await sendMessageWithImage(sessionId, file, newClientMsgId())
      // 轮询消息列表
      const maxAttempts = 90 // 约 180 秒，对齐后端 150s 外层超时
      const interval = 2000
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((resolve) => setTimeout(resolve, interval))
        if (!sending.value) return
        if (!isCurrent()) return // 会话已切换/元素已移除，停止旧轮询
        const res = await listMessages(sessionId)
        if (!isCurrent()) return // 轮询期间切换会话，写入前再次校验
        const msgs = res.data
        // 更新用户消息（识别后的问题）
        const lastUser = [...msgs].reverse().find((m) => m.role === 'user')
        if (lastUser && !lastUser.content.includes('[图片消息]')) {
          userMsgView.content = lastUser.content
        }
        // 查找 AI 回复
        const lastAssistant = [...msgs].reverse().find((m) => m.role === 'assistant')
        if (lastAssistant && lastAssistant.content) {
          aiMsgView.id = lastAssistant.id
          aiMsgView.content = lastAssistant.content
          aiMsgView.citations = lastAssistant.citations
          aiMsgView.tokens = lastAssistant.tokens
          aiMsgView.createdAt = lastAssistant.createdAt
          break
        }
      }
      if (isCurrent() && !aiMsgView.content) {
        aiMsgView.content = '图片识别超时，请稍后重试。'
      }
      // 刷新会话列表（获取自动命名的标题）
      loadSessions()
    } catch {
      ElMessage.error('图片上传失败，请重试')
      if (isCurrent() && !aiMsgView.content) {
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
