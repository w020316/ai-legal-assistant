import http from './request'
import type { ApiResult, PageQuery, PageResult } from './types'
import { useUserStore } from '@/stores/user'

// 健康检查
export interface HealthInfo {
  status: string
  service: string
  timestamp: string
}

export const getHealth = () => http.get<HealthInfo>('/health')

// 登录
export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export const login = (data: LoginParams) => http.post<LoginResult>('/auth/login', data)

// 注册
export interface RegisterParams {
  username: string
  password: string
  email?: string
}

export const register = (data: RegisterParams) => http.post<void>('/auth/register', data)

// ==================== 对话相关 ====================

// 引用来源（对应后端 RAG 检索片段）
export interface Citation {
  docId: number
  title: string
  source: string
  snippet: string
}

// 会话 VO
export interface SessionVO {
  id: number
  title: string
  starred: boolean
  createdAt: string
  updatedAt: string
}

// 消息 VO
export interface MessageVO {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  citations: Citation[] | null
  tokens: number | null
  createdAt: string
}

// 发送消息请求体
export interface SendMessageRequest {
  content: string
}

// 更新会话请求（重命名/收藏）
export interface UpdateSessionRequest {
  title?: string
  starred?: boolean
}

// 新建会话请求
export interface CreateSessionRequest {
  title?: string
}

// 会话列表查询参数
export interface SessionListQuery extends PageQuery {
  keyword?: string
  starred?: boolean
}

// 导出会话结果
export interface ExportResult {
  content: string
  filename: string
}

// SSE 事件回调集合
export interface SSECallbacks {
  onToken?: (content: string) => void
  onCitations?: (citations: Citation[]) => void
  onDone?: (data: { messageId: number; tokens: number }) => void
  onError?: (err: { code: number; message: string }) => void
}

// 会话列表（分页）
export const listSessions = (params?: SessionListQuery) =>
  http.get<PageResult<SessionVO>>('/sessions', { params })

// 新建会话
export const createSession = (data?: CreateSessionRequest) =>
  http.post<SessionVO>('/sessions', data)

// 更新会话（重命名/收藏）
export const updateSession = (id: number, data: UpdateSessionRequest) =>
  http.put<SessionVO>(`/sessions/${id}`, data)

// 删除会话
export const deleteSession = (id: number) => http.delete<void>(`/sessions/${id}`)

// 获取会话消息历史
export const listMessages = (sessionId: number) =>
  http.get<MessageVO[]>(`/sessions/${sessionId}/messages`)

// 导出会话为 Markdown
export const exportSession = (sessionId: number) =>
  http.post<ExportResult>(`/sessions/${sessionId}/export`)

// SSE 单个事件结构
interface SSEEvent {
  event: string
  data: string
}

// 解析 SSE 缓冲区，返回完整事件数组与剩余未完成片段
function parseSSEBuffer(buffer: string): { events: SSEEvent[]; rest: string } {
  const events: SSEEvent[] = []
  // 统一换行符，兼容 \r\n
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  // 末尾片段可能不完整，保留到下次解析
  const rest = parts.pop() || ''
  for (const part of parts) {
    if (!part.trim()) continue
    let event = 'message'
    const dataLines: string[] = []
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (dataLines.length) {
      events.push({ event, data: dataLines.join('\n') })
    }
  }
  return { events, rest }
}

// 分发单个 SSE 事件到对应回调
function dispatchSSEEvent(ev: SSEEvent, callbacks: SSECallbacks) {
  try {
    const payload = ev.data ? JSON.parse(ev.data) : {}
    switch (ev.event) {
      case 'token':
        callbacks.onToken?.(payload.content || '')
        break
      case 'citations':
        callbacks.onCitations?.(payload.citations || [])
        break
      case 'done':
        callbacks.onDone?.({ messageId: payload.messageId, tokens: payload.tokens })
        break
      case 'error':
        callbacks.onError?.({ code: payload.code || 9999, message: payload.message || '未知错误' })
        break
    }
  } catch {
    // JSON 解析失败，忽略单条事件
  }
}

// 发送消息（SSE 流式）
// 使用 fetch + ReadableStream 实现，支持 POST + Authorization 头（EventSource 不支持）
export async function sendMessageStream(
  sessionId: number,
  content: string,
  callbacks: SSECallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore()
  const token = userStore.token
  if (!token) {
    callbacks.onError?.({ code: 1002, message: '未登录' })
    return
  }
  const resp = await fetch(`/api/v1/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ content } satisfies SendMessageRequest),
    signal,
  })
  if (!resp.ok || !resp.body) {
    callbacks.onError?.({ code: resp.status, message: `请求失败（HTTP ${resp.status}）` })
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const { events, rest } = parseSSEBuffer(buffer)
      buffer = rest
      for (const ev of events) {
        dispatchSSEEvent(ev, callbacks)
      }
    }
    // 处理最后残留的不完整事件
    if (buffer.trim()) {
      const { events } = parseSSEBuffer(buffer + '\n\n')
      for (const ev of events) {
        dispatchSSEEvent(ev, callbacks)
      }
    }
  } finally {
    reader.releaseLock()
  }
}

// ==================== 文档分析相关 ====================

// 上传结果
export interface UploadResult {
  id: number
  filename: string
  fileType: string
  fileSize: number
}

// 风险点
export interface RiskPoint {
  clause: string
  level: string
  issue: string
  suggestion: string
  legalBasis: string
}

// 文档分析结果
export interface DocumentAnalysis {
  summary: string
  riskPoints: RiskPoint[]
}

// 用户文档
export interface UserDocumentVO {
  id: number
  filename: string
  fileType: string
  fileSize: number
  createdAt: string
  analysisStatus: string
}

// 上传文档（FormData）
export const uploadDocument = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<UploadResult>('/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 文档列表
export const listDocuments = () => http.get<UserDocumentVO[]>('/documents')

// 获取文档分析结果
export const getDocumentAnalysis = (id: number) =>
  http.get<DocumentAnalysis>(`/documents/${id}/analysis`)

// 触发文档分析
export const analyzeDocument = (id: number) => http.post<void>(`/documents/${id}/analyze`)

// ==================== 文书模板相关 ====================

// 模板 VO
export interface TemplateVO {
  id: number
  title: string
  category: string
  source: string
  rawText: string
}

// 生成请求
export interface GenerateRequest {
  elements: Record<string, string>
}

// 生成结果
export interface GenerateResult {
  content: string
}

// 模板列表
export const listTemplates = (category?: string) =>
  http.get<TemplateVO[]>('/templates', { params: { category } })

// 模板详情
export const getTemplate = (id: number) => http.get<TemplateVO>(`/templates/${id}`)

// 生成文书
export const generateDocument = (id: number, data: GenerateRequest) =>
  http.post<GenerateResult>(`/templates/${id}/generate`, data)

// ==================== 案例检索相关 ====================

// 案例检索请求
export interface CaseSearchRequest {
  keyword?: string
  cause?: string
  courtLevel?: string
  year?: number
  page?: number
  size?: number
}

// 案例 VO
export interface CaseVO {
  id: number
  title: string
  caseCause: string
  court: string
  year: number
  summary: string
  source: string
}

// 案例检索
export const searchCases = (params: CaseSearchRequest) => http.get<CaseVO[]>('/cases', { params })

// 重新导出类型
export type { ApiResult }
