# AI 法律助手 - 免费部署指南

> 零成本托管方案：Cloudflare Pages + Render + Neon PostgreSQL + Upstash Redis
> 替代原 Oracle Cloud 自建服务器方案（该方案已废弃）。

## 一、架构总览

```
用户 ──> Cloudflare Pages (前端静态)
              │  直连(跨域, 已配 CORS)
              ▼
        Render (Spring Boot 后端 :8080)
              │              │
              ▼              ▼
      Neon PostgreSQL   Upstash Redis
              │
              ▼
   AI 路由: GLM(主) → Agnes(辅)  (SSE 流式, 自动降级)
```

## 二、云端资源清单

| 组件 | 平台 | 用途 | 地址 |
|------|------|------|------|
| 前端 | Cloudflare Pages | 静态站点 (Vue3) | https://lawai-frontend.pages.dev |
| 后端 | Render (Free) | Spring Boot API | https://lawai-backend-vgk2.onrender.com |
| 数据库 | Neon PostgreSQL | 业务数据 | — |
| 缓存 | Upstash Redis | JWT 黑名单等 | — |
| 代码库 | GitHub | w020316/ai-legal-assistant (master) | — |

## 三、后端部署 (Render)

### 1. 创建 Web Service (Docker)
- 连接 GitHub 仓库 `w020316/ai-legal-assistant`，分支 `master`
- Runtime: **Docker**，Free 实例（Region: Singapore）
- 需配置启动命令/环境变量（见下）

### 2. 环境变量

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | 生产 profile |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 初始管理员 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Neon PostgreSQL 连接信息 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Upstash Redis |
| `JWT_SECRET` | JWT 签名密钥 |
| `AGNES_API_KEY` / `AGNES_BASE_URL` | Agnes AI（辅助模型，降级用） |
| `GLM_API_KEY` / `GLM_BASE_URL` / `GLM_MODEL` / `GLM_VISION_MODEL` | **主模型：智谱 GLM**（OpenAI 兼容 v4 端点）。配置 `GLM_API_KEY` 即自动以智谱为主，无需开关；默认文本模型 `glm-4.5-flash`（免费最强），图片识别走 `glm-4v-flash`；失败/配额耗尽自动降级 Agnes |
| `TACKLEKEY_API_KEY` / `TACKLEKEY_MODEL` / `TACKLEKEY_ENABLED` | （兼容别名，等价 GLM_*，可忽略） |

> `CORS_ALLOWED_ORIGINS` 不必配置：默认值已在 `application.yml` 中包含
> `https://lawai-frontend.pages.dev` 等前端域名。

### 3. 数据库初始化
首次部署后需初始化 Neon 数据库（执行仓库 `init/` 目录脚本）：
- `01-schema.sql`：建表
- `02-cases-seed.sql`：279 条法律案例种子数据

### 4. 部署触发
- 推送到 `master` 分支自动重新部署
- 可用 Render Dashboard 的 `Manual Deploy → Clear build cache & deploy`

## 四、前端部署 (Cloudflare Pages)

### 1. 构建
```bash
cd frontend
npm run build        # 产物输出到 dist/
```
构建时通过 `.env.production` 注入：
```
VITE_API_BASE_URL=https://lawai-backend-vgk2.onrender.com/api/v1
VITE_APP_TITLE=AI 法律助手
```

### 2. 上传
Cloudflare Dashboard → Workers & Pages → Create → **Upload your static files**
- 上传 `frontend/dist/` 整个目录（含 `index.html`、`assets/`、`_redirects`、`favicon.svg`）
- 或打包 `dist/` 为 zip 上传（Cloudflare 自动解压）
- 项目名 `lawai-frontend`，域名即 `lawai-frontend.pages.dev`

### 3. 跨域策略（关键）
前端请求**直接使用绝对地址**（`VITE_API_BASE_URL`），经后端 CORS 白名单放行。
`public/_redirects` 的 `/api/*` 代理仅为备用（手动上传时默认不激活）。
前端域名新增后，须在 `backend/src/main/resources/application.yml` 的
`lawai.cors.allowed-origins` 中加入并重新部署后端。

## 五、AI 模型路由

- **主模型**：智谱 GLM（`https://open.bigmodel.cn/api/paas/v4`，OpenAI 兼容）
  - 文本：`glm-4.5-flash`（免费系列中性能最强，200k 上下文 + 推理）
  - 图片/OCR：`glm-4v-flash`（免费视觉模型）
- **辅助模型**：Agnes
- 配置 `GLM_API_KEY` 即自动以智谱为主（无需开关），支持同步 Chat 与 SSE 流式（`streamChat`）
- 主模型失败/配额耗尽自动降级到 Agnes（日志 `warn` 记录，`onErrorResume`）

## 六、常见问题

### Q: 带 Origin 的 API 返回 403？
A: 后端 CORS 白名单不含该前端域名。在 `application.yml` 的
`lawai.cors.allowed-origins` 加入域名（或配置 `CORS_ALLOWED_ORIGINS` 环境变量），
重新部署后端后验证响应含 `access-control-allow-origin`。

### Q: 登录/聊天返回 403（外部 Redis 不可用）？
A: 已实现 fail-open：`JwtAuthenticationFilter` 与 `AuthService` 中黑名单查询
在 Redis 异常时放行请求（告警但不断言），保证登录/聊天可用；Redis 恢复后黑名单
立即生效。完全恢复需保证 Upstash Redis 连通。

### Q: SSE 流式对话不响应？
A: 检查 Render 日志前端 `POST /api/v1/sessions/{id}/stream` 是否走主模型
`TacklekeyClient`；失败会降级并打印 warn。

### Q: Free 实例偶发请求失败？
A: Render Free 实例有冷启动，首次请求可能超时，重试即可。

## 七、本地开发

```bash
# 后端
cd backend
mvn spring-boot:run        # 默认 localhost:8080，需本地 PG/Redis 或指向云端

# 前端
cd frontend
npm install
npm run dev                # localhost:5173，/api 代理到 backend
```