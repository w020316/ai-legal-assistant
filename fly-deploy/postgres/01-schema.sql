-- AI 法律助手数据库初始化脚本
-- 由 PostgreSQL Docker 镜像在首次启动时自动执行

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 启用中文全文检索支持
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(64)  UNIQUE NOT NULL,
    email        VARCHAR(128) UNIQUE,
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(16)  NOT NULL DEFAULT 'LAWYER',
    status       SMALLINT     NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN sys_user.role   IS '角色：LAWYER 法律从业者 / ADMIN 管理员';
COMMENT ON COLUMN sys_user.status IS '状态：1 正常 0 禁用';

-- ============================================================
-- 会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    title      VARCHAR(128) NOT NULL DEFAULT '新对话',
    starred    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_session_user ON chat_session(user_id, updated_at DESC);

-- ============================================================
-- 消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    content    TEXT        NOT NULL,
    citations  JSONB,
    tokens     INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON chat_message(session_id, created_at);
COMMENT ON COLUMN chat_message.role      IS '角色：user / assistant';
COMMENT ON COLUMN chat_message.citations IS '引用来源数组';

-- ============================================================
-- 知识文档表（公共 + 个人）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id         BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(16)  NOT NULL,
    owner_id   BIGINT,
    doc_type   VARCHAR(32)  NOT NULL,
    title      VARCHAR(256) NOT NULL,
    source     VARCHAR(256),
    metadata   JSONB,
    raw_text   TEXT,
    status     SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_doc_owner ON knowledge_doc(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_doc_type  ON knowledge_doc(doc_type);
COMMENT ON COLUMN knowledge_doc.owner_type IS '归属：PUBLIC 公共 / PRIVATE 个人';
COMMENT ON COLUMN knowledge_doc.doc_type   IS '类型：LAW 法规 / CASE 案例 / TEMPLATE 模板 / GUIDE 指南 / USER_UPLOAD 用户上传';
COMMENT ON COLUMN knowledge_doc.status     IS '状态：0 待处理 1 已向量化';

-- ============================================================
-- 知识切片与向量表（pgvector）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      BIGINT NOT NULL REFERENCES knowledge_doc(id) ON DELETE CASCADE,
    chunk_index INT    NOT NULL,
    content     TEXT   NOT NULL,
    embedding   VECTOR(1024),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc      ON knowledge_chunk(doc_id);
-- 向量索引（lists 参数按数据量调整，初始 100）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON knowledge_chunk
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ============================================================
-- 上传文档表
-- ============================================================
CREATE TABLE IF NOT EXISTS user_document (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    filename        VARCHAR(256) NOT NULL,
    file_type       VARCHAR(16),
    file_size       BIGINT,
    ocr_text        TEXT,
    analysis_result JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_doc ON user_document(user_id, created_at DESC);

-- ============================================================
-- 审计日志
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT,
    action     VARCHAR(64) NOT NULL,
    ip         VARCHAR(64),
    detail     JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(user_id, created_at DESC);

-- ============================================================
-- 初始化管理员（密码: admin123，bcrypt 哈希，M2 阶段由后端首次启动动态创建）
-- 此处仅占位，实际生产由后端启动时检查并创建
-- ============================================================
