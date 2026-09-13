#!/bin/bash
# HF Spaces 启动脚本
set -e

echo "===== AI 法律助手 HF Spaces 启动 ====="

# v1.18：强制 prod profile，防止公网 Space 落到 dev 默认配置（公开 dev JWT 密钥、admin/admin123、Swagger 开放）
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

# 检查必需的环境变量
if [ -z "$AGNES_API_KEY" ]; then
    echo "ERROR: AGNES_API_KEY 未设置，请在 HF Space Settings → Repository secrets 中配置"
    exit 1
fi
if [ -z "$DB_HOST" ]; then
    echo "ERROR: DB_HOST 未设置，请配置 Supabase 数据库连接"
    exit 1
fi
if [ -z "$REDIS_HOST" ]; then
    echo "ERROR: REDIS_HOST 未设置，请配置 Upstash Redis 连接"
    exit 1
fi

echo "数据库: $DB_HOST:$DB_PORT/$DB_NAME"
echo "Redis: $REDIS_HOST:$REDIS_PORT"
echo "AI: $AGNES_BASE_URL"

# 删除 Nginx 默认站点（避免冲突）
rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default

# 启动 supervisord（管理 Nginx + Java）
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/app.conf
