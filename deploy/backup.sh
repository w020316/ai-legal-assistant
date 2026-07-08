#!/bin/bash
# AI 法律助手 - PostgreSQL 每日备份脚本
# crontab: 0 3 * * * /opt/lawai/deploy/backup.sh
set -e

BACKUP_DIR="/opt/lawai/backups"
DB_USER="lawai"
DB_NAME="lawai"
KEEP_DAYS=7
DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/lawai_$DATE.sql.gz"

mkdir -p $BACKUP_DIR

echo "开始备份 $DB_NAME ..."

# 通过 docker exec 执行 pg_dump
docker exec lawai-postgres pg_dump -U $DB_USER $DB_NAME | gzip > $FILE

echo "备份完成: $FILE ($(du -h $FILE | cut -f1))"

# 清理旧备份
find $BACKUP_DIR -name "lawai_*.sql.gz" -mtime +$KEEP_DAYS -delete
echo "已清理 $KEEP_DAYS 天前的旧备份"
