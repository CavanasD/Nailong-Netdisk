package com.nailong.netdisk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(2)
public class RbacSchemaPatcher implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        createTables();
        initData();
        migrateUserRoles();
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(50) NOT NULL UNIQUE," +
                "description VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_permission (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL UNIQUE," +
                "description VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_user_role (" +
                "user_id BIGINT NOT NULL," +
                "role_id BIGINT NOT NULL," +
                "PRIMARY KEY (user_id, role_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role_permission (" +
                "role_id BIGINT NOT NULL," +
                "permission_id BIGINT NOT NULL," +
                "PRIMARY KEY (role_id, permission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void initData() {
        insertRoleIfNotExists(1L, "SUPER_ADMIN", "超级管理员");
        insertRoleIfNotExists(2L, "ADMIN", "管理员");
        insertRoleIfNotExists(3L, "USER", "普通用户");

        // Example permissions
        insertPermissionIfNotExists(1L, "system:admin:access", "后台访问权限");
        insertPermissionIfNotExists(2L, "user:list", "用户列表查看");
        insertPermissionIfNotExists(3L, "file:upload", "文件上传");

        insertRolePermissionIfNotExists(1L, 1L);
        insertRolePermissionIfNotExists(1L, 2L);
        insertRolePermissionIfNotExists(1L, 3L);

        insertRolePermissionIfNotExists(2L, 1L);
        insertRolePermissionIfNotExists(2L, 2L);

        insertRolePermissionIfNotExists(3L, 3L);
    }

    private void migrateUserRoles() {
        try {
            // Check if column 'role' exists to avoid errors on fresh install without the old schema
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("SHOW COLUMNS FROM sys_user LIKE 'role'");
            if (columns.isEmpty()) {
                return;
            }

            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT user_id, role FROM sys_user");
            for (Map<String, Object> user : users) {
                Long userId = ((Number) user.get("user_id")).longValue();
                String roleName = (String) user.get("role");

                if (roleName != null) {
                    Long roleId;
                    if ("super_admin".equalsIgnoreCase(roleName)) {
                        roleId = 1L;
                    } else if ("admin".equalsIgnoreCase(roleName)) {
                        roleId = 2L;
                    } else {
                        roleId = 3L;
                    }

                    String checkSql = "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = ?";
                    Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, roleId);
                    if (count == null || count == 0) {
                        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Migration warning: " + e.getMessage());
        }
    }

    private void insertRoleIfNotExists(Long id, String name, String description) {
        try {
            jdbcTemplate.update("INSERT IGNORE INTO sys_role (id, name, description) VALUES (?, ?, ?)", id, name, description);
        } catch (Exception e) {
            // ignore
        }
    }

    private void insertPermissionIfNotExists(Long id, String name, String description) {
        try {
            jdbcTemplate.update("INSERT IGNORE INTO sys_permission (id, name, description) VALUES (?, ?, ?)", id, name, description);
        } catch (Exception e) {
            // ignore
        }
    }

    private void insertRolePermissionIfNotExists(Long roleId, Long permissionId) {
        try {
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        } catch (Exception e) {
            // ignore
        }
    }
}


