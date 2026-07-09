#!/bin/bash
set -e

# Fly.io volume 挂载为 root:root 0755，postgres 用户(uid 999)无法在挂载点创建子目录
# 以 root 身份预处理：创建 PGDATA 子目录并授权给 postgres 用户

MOUNT_POINT="/var/lib/postgresql/data"
PGDATA_DIR="${PGDATA:-/var/lib/postgresql/data/pgdata}"

# 确保挂载点存在且可写（root 身份）
mkdir -p "$MOUNT_POINT"

# 创建 PGDATA 子目录
mkdir -p "$PGDATA_DIR"

# 授权给 postgres 用户（uid 999）
chown -R postgres:postgres "$MOUNT_POINT"

echo "[fly-entrypoint] PGDATA=$PGDATA_DIR 已创建并授权"

# 切换到 postgres 用户执行原始 docker-entrypoint
exec gosu postgres docker-entrypoint.sh "$@"
