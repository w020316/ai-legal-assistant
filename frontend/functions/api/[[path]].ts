// Cloudflare Pages Function：边缘反向代理 /api/* → Render 后端
// 用途：让前端只请求同源 pages.dev，由 Cloudflare 边缘代为转发，绕开国内直连 onrender 不可达，
// 且同源访问无需 CORS、支持 SSE 流式转发。
// 请求路径例如：/api/v1/auth/login → 转 https://lawai-backend-vgk2.onrender.com/api/v1/auth/login
const BACKEND_ORIGIN = 'https://lawai-backend-vgk2.onrender.com'

export const onRequest: PagesFunction<Env> = async (ctx) => {
  const { request, params } = ctx
  const url = new URL(request.url)

  // 拼接后端路径：/api/<path>
  const pathParts: string[] = []
  const p = params.path
  if (Array.isArray(p)) pathParts.push(...p.map((s) => decodeURIComponent(s)))
  else if (typeof p === 'string') pathParts.push(decodeURIComponent(p))
  const backendPath = '/api/' + pathParts.join('/')

  const backendUrl = BACKEND_ORIGIN + backendPath + url.search

  const method = request.method
  const init: RequestInit = { method, headers: request.headers } as RequestInit
  if (method !== 'GET' && method !== 'HEAD') {
    init.body = await request.arrayBuffer()
  }

  // 转发并透传状态码/头/Body（含 SSE 流式）
  return fetch(backendUrl, init)
}