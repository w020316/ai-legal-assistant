FROM pgvector/pgvector:pg16

# 内置建表脚本，首次 initdb 时自动执行
COPY 01-schema.sql /docker-entrypoint-initdb.d/01-schema.sql

# 复制 Fly.io 专用 entrypoint（处理 volume 挂载权限问题）
COPY fly-entrypoint.sh /fly-entrypoint.sh

# 处理可能的 Windows CRLF 换行符问题，确保脚本能执行
RUN sed -i 's/\r$//' /fly-entrypoint.sh /docker-entrypoint-initdb.d/01-schema.sql && chmod +x /fly-entrypoint.sh

# 覆盖 entrypoint，保持 CMD 为 postgres
ENTRYPOINT ["/fly-entrypoint.sh"]
CMD ["postgres"]
