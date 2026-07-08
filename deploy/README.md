# AI 法律助手 - 部署指南

> 零成本部署方案：Oracle Cloud Free Tier + Cloudflare + .eu.org 免费域名

## 一、系统要求

- Oracle Cloud Free Tier ARM 实例（Ampere A1，4核24GB，永久免费）
- Ubuntu 22.04 LTS
- 开放端口：80（HTTP）、443（HTTPS）、22（SSH）

## 二、前置准备

### 1. 注册 Oracle Cloud
- 访问 https://www.oracle.com/cloud/free/
- 注册账号（需信用卡验证，不扣费）
- 创建 ARM 实例：Ampere A1，4 OCPU，24GB 内存，Ubuntu 22.04
- 保存 SSH 密钥

### 2. 申请免费域名
- 访问 https://nic.eu.org/
- 申请 `yourname.eu.org` 域名（数天内审核通过）
- 或使用已有域名

### 3. 配置 Cloudflare
- 注册 https://www.cloudflare.com/
- 添加站点（你的域名）
- 将域名 NS 服务器改为 Cloudflare 提供的 NS
- SSL/TLS → 模式设为 `Full (strict)`
- Origin Server → Create Certificate → 生成 Origin Certificate（15年有效期）
- 保存 `origin.pem`（证书）和 `key.pem`（私钥）

## 三、部署步骤

### 1. SSH 登录服务器
```bash
ssh ubuntu@your-server-ip
```

### 2. 一键部署
```bash
sudo su
mkdir -p /opt/lawai && cd /opt/lawai
# 克隆项目
git clone https://github.com/your-repo/ai-legal-assistant.git .

# 运行部署脚本
chmod +x deploy/setup.sh
./deploy/setup.sh
```

### 3. 配置环境变量
```bash
nano .env
# 填写 DB_PASSWORD / REDIS_PASSWORD / AGNES_API_KEY / JWT_SECRET
```

### 4. 放置 SSL 证书
```bash
# 将 Cloudflare 生成的证书内容粘贴
nano nginx/certs/origin.pem  # 粘贴证书
nano nginx/certs/key.pem     # 粘贴私钥
```

### 5. 启动服务
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

### 6. 验证
```bash
# 健康检查
curl http://localhost:8080/api/v1/health

# 查看日志
docker compose -f docker-compose.prod.yml logs -f backend
```

## 四、Cloudflare 配置

1. DNS → 添加 A 记录：`lawai` → 服务器 IP，Proxy 开启（橙色云朵）
2. SSL/TLS → Edge Certificates → 启用 Always Use HTTPS
3. Speed → Optimization → 开启 Brotli 压缩
4. Caching → Configuration → Browser Cache TTL 设为 1 年

## 五、国内访问优化

Cloudflare 优选 IP 提升国内访问速度：

```bash
# 使用社区优选 IP 工具
# https://github.com/XIU2/CloudflareSpeedTest
./CloudflareST -dn 10 -sl 0.05

# 将优选 IP 填入 Cloudflare DNS 的 A 记录
```

## 六、数据库备份

```bash
# 配置每日凌晨3点自动备份
crontab -e
# 添加：
0 3 * * * /opt/lawai/deploy/backup.sh >> /opt/lawai/backups/backup.log 2>&1
```

## 七、更新部署

```bash
cd /opt/lawai
git pull origin main
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f
```

或配置 GitHub Actions 自动部署（推送到 main 分支自动部署）：
- 在 GitHub 仓库 Settings → Secrets 添加：
  - `ORACLE_HOST`：服务器 IP
  - `ORACLE_USER`：SSH 用户名
  - `ORACLE_SSH_KEY`：SSH 私钥

## 八、常见问题

### Q: Docker 构建慢？
A: 配置国内镜像加速：`/etc/docker/daemon.json` 添加 `registry-mirrors`

### Q: pgvector 扩展未安装？
A: 使用 `pgvector/pgvector:pg16` 镜像已内置，init/01-schema.sql 会自动 CREATE EXTENSION

### Q: SSE 流式不工作？
A: 检查 Nginx 配置 `proxy_buffering off`，Cloudflare 默认支持 SSE

### Q: Agnes AI 调用失败？
A: 检查 .env 中 AGNES_API_KEY 是否正确，服务器能否访问 agnes-ai.com

## 九、架构图

```
用户 → Cloudflare(CDN/SSL) → Oracle Cloud Server
                                  ↓
                            Nginx (443)
                              ↓        ↓
                       前端静态    后端API(:8080)
                                      ↓
                              PostgreSQL + Redis
                                      ↓
                              Agnes AI (外部API)
```
