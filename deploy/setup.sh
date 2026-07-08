#!/bin/bash
# AI 法律助手 - Oracle Cloud 一键部署脚本
set -e

echo "===== AI 法律助手部署脚本 ====="

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "[1/6] 安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
else
    echo "[1/6] Docker 已安装"
fi

# 检查 docker compose
if ! docker compose version &> /dev/null; then
    echo "docker compose 不可用，请安装 Docker Compose V2"
    exit 1
fi

# 创建目录
APP_DIR="/opt/lawai"
echo "[2/6] 创建应用目录 $APP_DIR ..."
mkdir -p $APP_DIR/nginx/certs $APP_DIR/nginx/html $APP_DIR/backups
cd $APP_DIR

# 克隆仓库（若不存在）
if [ ! -d "$APP_DIR/.git" ]; then
    echo "[3/6] 克隆仓库..."
    read -p "请输入仓库 Git 地址: " REPO_URL
    git clone $REPO_URL .
else
    echo "[3/6] 更新代码..."
    git pull origin main || true
fi

# 配置环境变量
ENV_FILE="$APP_DIR/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "[4/6] 生成 .env 配置文件..."
    cat > $ENV_FILE << 'ENVEOF'
DB_USER=lawai
DB_PASSWORD=请修改为强密码
DB_NAME=lawai
REDIS_PASSWORD=请修改为强密码
AGNES_API_KEY=请填入Agnes密钥
AGNES_BASE_URL=https://agnes-ai.com
JWT_SECRET=请修改为至少32位随机字符串
JWT_ACCESS_EXPIRATION=7200000
JWT_REFRESH_EXPIRATION=604800000
ENVEOF
    echo "  已生成 $ENV_FILE，请编辑后重新运行此脚本"
    echo "  命令: nano $ENV_FILE"
    exit 0
else
    echo "[4/6] .env 已存在"
fi

# 生成自签名证书（若没有 Cloudflare Origin Certificate）
CERT_DIR="$APP_DIR/nginx/certs"
if [ ! -f "$CERT_DIR/origin.pem" ]; then
    echo "[5/6] 生成临时自签名证书（建议替换为 Cloudflare Origin Certificate）..."
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout $CERT_DIR/key.pem \
        -out $CERT_DIR/origin.pem \
        -subj "/C=CN/ST=Beijing/L=Beijing/O=LawAI/CN=lawai"
else
    echo "[5/6] SSL 证书已存在"
fi

# 防火墙
echo "[6/6] 配置防火墙..."
if command -v ufw &> /dev/null; then
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw allow 22/tcp
    ufw --force enable
elif command -v firewall-cmd &> /dev/null; then
    firewall-cmd --permanent --add-port=80/tcp
    firewall-cmd --permanent --add-port=443/tcp
    firewall-cmd --reload
fi

# 启动
echo "启动服务..."
docker compose -f docker-compose.prod.yml up -d --build

echo ""
echo "===== 部署完成 ====="
echo "后端: http://$(curl -s ifconfig.me):8080/api/v1/health"
echo ""
echo "后续步骤："
echo "1. 申请免费域名: https://nic.eu.org/"
echo "2. 注册 Cloudflare: https://www.cloudflare.com/"
echo "3. 在 Cloudflare 添加域名 DNS 指向本机 IP"
echo "4. SSL/TLS 设置为 Full (strict)"
echo "5. 生成 Origin Certificate 替换 $CERT_DIR/origin.pem 和 key.pem"
echo "6. 重启 nginx: docker compose -f docker-compose.prod.yml restart nginx"
echo ""
echo "数据库备份: 配置 crontab 每日执行 deploy/backup.sh"
