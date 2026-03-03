package com.nailong.netdisk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class FileSchemaPatcher implements CommandLineRunner {

    private static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        patchUserQuotaColumns();
        createFileTable();
        patchRecycleColumns();
    }

    private void patchUserQuotaColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN storage_used BIGINT DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN storage_quota BIGINT DEFAULT " + DEFAULT_QUOTA_BYTES);
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN avatar_path VARCHAR(500)");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.update("UPDATE sys_user SET storage_quota = ? WHERE storage_quota IS NULL", DEFAULT_QUOTA_BYTES);
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.update("UPDATE sys_user SET storage_used = 0 WHERE storage_used IS NULL");
        } catch (Exception ignored) {
        }
    }

    private void createFileTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_file (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "original_name VARCHAR(255) NOT NULL," +
                "stored_name VARCHAR(255) NOT NULL," +
                "content_type VARCHAR(100)," +
                "size BIGINT NOT NULL," +
                "storage_path VARCHAR(500) NOT NULL," +
                "create_time DATETIME NOT NULL," +
                "trashed TINYINT NOT NULL DEFAULT 0," +
                "trash_time DATETIME NULL," +
                "expire_time DATETIME NULL," +
                "trashed_by BIGINT NULL," +
                "INDEX idx_sys_file_user_time (user_id, create_time)," +
                "INDEX idx_sys_file_user_trashed (user_id, trashed)," +
                "INDEX idx_sys_file_expire_time (expire_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void patchRecycleColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE sys_file ADD COLUMN trashed TINYINT NOT NULL DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE sys_file ADD COLUMN trash_time DATETIME NULL");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE sys_file ADD COLUMN expire_time DATETIME NULL");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE sys_file ADD COLUMN trashed_by BIGINT NULL");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("CREATE INDEX idx_sys_file_user_trashed ON sys_file (user_id, trashed)");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("CREATE INDEX idx_sys_file_expire_time ON sys_file (expire_time)");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.update("UPDATE sys_file SET trashed = 0 WHERE trashed IS NULL");
        } catch (Exception ignored) {
        }
    }
}
