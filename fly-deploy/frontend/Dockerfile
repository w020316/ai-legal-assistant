# Fly.io 前端：基于 ghcr.io 预构建镜像，修改 nginx 反向代理地址为 Fly 内部域名
FROM ghcr.io/w020316/lawai-frontend:latest
# 将 nginx.conf 中的 backend:8080 替换为 Fly.io 内部网络地址
RUN sed -i 's|proxy_pass http://backend:8080|proxy_pass http://lawai-backend.internal:8080|' /etc/nginx/conf.d/default.conf
