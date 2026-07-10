import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from './types'
import { useUserStore } from '@/stores/user'

// 错误码映射（对应后端 ResultCode）
const ERROR_MESSAGES: Record<number, string> = {
  1001: '参数错误',
  1002: '登录已过期，请重新登录',
  1003: '无权限访问',
  1004: '资源不存在',
  1005: '资源已存在',
  1006: '请求过于频繁，请稍后再试',
  2001: 'AI 服务暂时不可用',
  2002: '知识检索失败',
  2003: '文件超出大小限制',
  9999: '服务暂时不可用，请稍后重试',
}

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截：注入 Token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ===== Token 自动刷新机制 =====
let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

function onTokenRefreshed(newToken: string) {
  pendingRequests.forEach((cb) => cb(newToken))
  pendingRequests = []
}

function addPendingRequest(cb: (token: string) => void) {
  pendingRequests.push(cb)
}

// 尝试用 refreshToken 续期；成功返回新 token，失败返回 null
async function tryRefreshToken(): Promise<string | null> {
  const userStore = useUserStore()
  if (!userStore.refreshToken) return null
  try {
    const res = await axios.post<ApiResult<{ accessToken: string; refreshToken: string; username: string; role: string }>>(
      '/api/v1/auth/refresh',
      { refreshToken: userStore.refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    )
    if (res.data.code === 0 && res.data.data.accessToken) {
      userStore.setAuth({
        accessToken: res.data.data.accessToken,
        refreshToken: res.data.data.refreshToken,
        username: res.data.data.username || userStore.username,
        role: res.data.data.role || userStore.role,
      })
      return res.data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

// 清除登录态并跳转登录页
function forceLogout() {
  const userStore = useUserStore()
  userStore.logout()
  window.location.href = '/login'
}

// 响应拦截：统一处理业务码 + 401 自动刷新
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    // 流式响应（SSE/Blob）直接返回原始数据
    if (response.config.responseType === 'stream' || response.config.responseType === 'blob') {
      return response.data
    }
    if (res.code === 0) {
      // 业务成功：直接返回 ApiResult，调用处用 res.data 取业务数据
      return res
    }
    // 业务错误
    const msg = ERROR_MESSAGES[res.code] || res.message || '请求失败'
    // 1002：Token 过期 — 尝试自动刷新
    if (res.code === 1002) {
      const originalConfig = response.config
      if (!isRefreshing) {
        isRefreshing = true
        tryRefreshToken().then((newToken) => {
          if (newToken) {
            onTokenRefreshed(newToken)
            // 静默续期，不弹消息
          } else {
            pendingRequests = []
            forceLogout()
          }
          isRefreshing = false
        })
      }
      // 将当前请求挂起，等 refresh 完成后自动重试
      return new Promise((resolve, reject) => {
        addPendingRequest((token) => {
          originalConfig.headers.Authorization = `Bearer ${token}`
          service(originalConfig).then(resolve).catch(reject)
        })
      })
    }
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  async (error) => {
    const status = error.response?.status
    const originalConfig = error.config

    // HTTP 401：尝试自动刷新 Token 后重试一次
    const retried = (originalConfig as InternalAxiosRequestConfig & { _retried?: boolean })?._retried
    if (status === 401 && originalConfig && !retried) {
      if (!isRefreshing) {
        isRefreshing = true
        const newToken = await tryRefreshToken()
        isRefreshing = false
        if (newToken) {
          onTokenRefreshed(newToken)
          ;(originalConfig as InternalAxiosRequestConfig & { _retried?: boolean })._retried = true
          originalConfig.headers.Authorization = `Bearer ${newToken}`
          return service(originalConfig)
        }
        pendingRequests = []
        forceLogout()
        return Promise.reject(error)
      }
      // 正在刷新：挂起等待
      return new Promise((resolve, reject) => {
        addPendingRequest((token) => {
          ;(originalConfig as InternalAxiosRequestConfig & { _retried?: boolean })._retried = true
          originalConfig.headers.Authorization = `Bearer ${token}`
          service(originalConfig).then(resolve).catch(reject)
        })
      })
    }

    let msg = '网络异常，请检查连接'
    if (status === 401) msg = '登录已过期，请重新登录'
    else if (status === 403) msg = '无权限访问'
    else if (status === 429) msg = '请求过于频繁，请稍后再试'
    else if (status >= 500) msg = '服务暂时不可用，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

// 导出封装方法，统一返回 ApiResult<T>，调用处用 res.data 取业务数据
const http = {
  get<T = unknown>(url: string, config?: Parameters<typeof service.get>[1]) {
    return service.get<T, ApiResult<T>>(url, config)
  },
  post<T = unknown>(url: string, data?: unknown, config?: Parameters<typeof service.post>[2]) {
    return service.post<T, ApiResult<T>>(url, data, config)
  },
  put<T = unknown>(url: string, data?: unknown, config?: Parameters<typeof service.put>[2]) {
    return service.put<T, ApiResult<T>>(url, data, config)
  },
  delete<T = unknown>(url: string, config?: Parameters<typeof service.delete>[1]) {
    return service.delete<T, ApiResult<T>>(url, config)
  },
  // 原始 axios 实例，用于 SSE/上传等特殊场景
  raw: service,
}

export default http
