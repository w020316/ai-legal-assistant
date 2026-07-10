import http from './request'
import type { ApiResult, PageQuery, PageResult } from './types'

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
  username: string
  role: string
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

// SSE 事件回调集合（保留用于未来可能的流式升级）
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

// 发送消息（异步模式，返回用户消息 ID，AI 回复通过轮询获取）
export const sendMessage = (sessionId: number, content: string) =>
  http.post<number>(`/sessions/${sessionId}/messages`, { content } satisfies SendMessageRequest)

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
