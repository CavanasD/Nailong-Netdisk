package com.nailong.netdisk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseAutoPatcher implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            // 尝试自动添加缺失的 role 字段，解决 "Unknown column 'role'" 错误
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN role VARCHAR(50) DEFAULT 'USER'");
            System.out.println("数据库补丁执行成功：已添加 role 字段");
        } catch (Exception e) {
            // 如果字段已存在，会报错，这里忽略即可
        }
    }
}

