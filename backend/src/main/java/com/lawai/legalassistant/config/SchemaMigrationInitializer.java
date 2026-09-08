package com.lawai.legalassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据库幂等迁移（v1.13.1）
 * <p>
 * 应用启动时补充必要的幂等 DDL，自愈线上 Neon 数据库未执行的手动迁移。
 * 当前补齐 v1.12 新增的 user_document.version 列（ADD COLUMN IF NOT EXISTS，重复执行安全）。
 */
@Component
public class SchemaMigrationInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationInitializer.class);

    private final JdbcTemplate jdbc;

    public SchemaMigrationInitializer(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateVersionColumn();
    }

    private void migrateVersionColumn() {
        try {
            jdbc.execute(
                    "ALTER TABLE user_document "
                            + "ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1");
            log.info("[schema] user_document.version 列已确保存在（幂等）");
        } catch (Exception e) {
            log.warn("[schema] user_document.version 迁移失败，已跳过: {}", e.getMessage());
        }
    }
}