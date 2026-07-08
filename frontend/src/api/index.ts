import http from './request'
import type { ApiResult } from './types'

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

// 重新导出类型
export type { ApiResult }
