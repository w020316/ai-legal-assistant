# AI 法律助手 - Hugging Face Spaces 部署
# 单容器运行 Nginx(前端) + Java(后端)，连接外部 Supabase(PG) + Upstash(Redis)
# HF Spaces 要求监听 7860 端口
#
# 部署步骤：
# 1. 在 huggingface.co 创建新 Space，SDK 选 Docker
# 2. 将 hf-space/ 目录下所有文件推送到 Space 仓库
# 3. 在 Space Settings → Repository secrets 配置环境变量（见 README）

# 阶段1：从前端镜像提取静态文件
FROM ghcr.io/w020316/lawai-frontend:latest AS frontend

# 阶段2：从后端镜像提取 jar
FROM ghcr.io/w020316/lawai-backend:latest AS backend

# 阶段3：运行时（JRE + Nginx + Supervisord）
FROM eclipse-temurin:21-jre

# 安装 Nginx + Supervisord
RUN apt-get update && \
    apt-get install -y --no-install-recommends nginx supervisor curl tzdata && \
    rm -rf /var/lib/apt/lists/* && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# 复制前端静态文件
COPY --from=frontend /usr/share/nginx/html /usr/share/nginx/html

# 复制后端 jar
COPY --from=backend /app/app.jar /app/app.jar

# 复制配置文件
COPY nginx.conf /etc/nginx/sites-available/default
COPY supervisord.conf /etc/supervisor/conf.d/app.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# HF Spaces 要求 7860 端口
EXPOSE 7860

ENTRYPOINT ["/entrypoint.sh"]
