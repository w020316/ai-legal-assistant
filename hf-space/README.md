# AI 法律助手 - Hugging Face Spaces 部署

永久免费部署方案：HF Spaces（前端+后端）+ Supabase（PostgreSQL+pgvector）+ Upstash（Redis）

## 部署步骤

### 第一步：注册 Supabase（免费 PostgreSQL + pgvector）

1. 访问 https://supabase.com 用 GitHub 登录
2. New Project → 填写项目名 → 创建（免费 500MB）
3. 进入 SQL Editor → 新建 Query → 粘贴 `init/01-schema.sql` 内容 → Run
4. 进入 Project Settings → Database → Connection string
5. 记下连接信息：
   - Host: `db.xxxxx.supabase.co`
   - Port: `5432`
   - Database: `postgres`
   - User: `postgres`
   - Password: 你创建项目时设的密码

### 第二步：注册 Upstash（免费 Redis）

1. 访问 https://upstash.com 用 GitHub 登录
2. Create Database → 填名 → 选 region（建议 AWS ap-northeast-1 日本）→ Create
3. 记下连接信息（页面会显示）：
   - Endpoint: `xxx.upstash.io`
   - Port: `6379`
   - Password: `xxxxxx`

### 第三步：创建 Hugging Face Space

1. 访问 https://huggingface.co 登录（无账号则注册，免费）
2. 右上角头像 → New Space
   - Space name: `ai-legal-assistant`
   - License: MIT
   - SDK: **Docker**
   - Space Hardware: **CPU basic (2 vCPU 16GB) Free**
   - 可见性: Public
3. Create Space

### 第四步：推送代码到 HF Space

在本地执行（替换 `你的用户名`）：

```bash
cd d:\xm\wz\ai法律助手\hf-space
git init
git remote add space https://huggingface.co/spaces/你的用户名/ai-legal-assistant
git add .
git commit -m "deploy: AI法律助手 HF Spaces"
git push space main -f
```

### 第五步：配置环境变量

在 HF Space 页面 → Settings → **Repository secrets** → New secret，逐个添加：

| Key | Value |
|---|---|
| `AGNES_API_KEY` | `sk-Cw3WSNHCOBCcogmkg9AzyIL41kks5ntryA6O3QRGnprgN2sy` |
| `AGNES_BASE_URL` | `https://agnes-ai.com` |
| `DB_HOST` | Supabase 的 Host（如 `db.abc.supabase.co`） |
| `DB_PORT` | `5432` |
| `DB_NAME` | `postgres` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | Supabase 项目密码 |
| `REDIS_HOST` | Upstash Endpoint（如 `xxx.upstash.io`） |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | Upstash 密码 |
| `JWT_SECRET` | `lawai-prod-jwt-secret-32-chars-minimum-2026` |

### 第六步：访问

配置完 secrets 后，Space 会自动重新构建。构建完成后访问：

```
https://你的用户名-ai-legal-assistant.hf.space
```

登录：admin / admin123

## 免费额度说明

| 平台 | 免费额度 | 是否永久 |
|---|---|---|
| HF Spaces | 2核16G + 50GB 临时磁盘 | 永久免费 |
| Supabase | 500MB 数据库 + API | 永久免费 |
| Upstash | 10000 命令/天 | 永久免费 |

数据存储在 Supabase 和 Upstash（外部托管），HF Spaces 重启不影响数据。

## 更新部署

代码修改后，GitHub Actions 自动构建镜像到 ghcr.io。HF Spaces 重新 push 即可拉取最新镜像。
