# PostgreSQL + pgvector + 初始化脚本
# 将建表脚本内置到镜像中，云平台部署无需挂载 init 目录
FROM pgvector/pgvector:pg16
COPY init/01-schema.sql /docker-entrypoint-initdb.d/01-schema.sql
