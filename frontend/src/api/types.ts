// 统一响应结构（对应后端 Result<T>）
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}

// 分页响应
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  pages: number
  current: number
  size: number
}

// 分页请求
export interface PageQuery {
  page?: number
  size?: number
}
