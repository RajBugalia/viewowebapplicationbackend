package com.pixl.backend.config

import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class DatabaseInitializer(
    private val jdbcTemplate: JdbcTemplate
) : CommandLineRunner {

    override fun run(vararg args: String) {
        try {
            val statements = listOf(
                "CREATE TABLE IF NOT EXISTS users (id BIGSERIAL PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, password_hash VARCHAR(255) NOT NULL, role VARCHAR(255) NOT NULL)",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS theme VARCHAR(50) DEFAULT 'light'",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC'",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_alerts BOOLEAN DEFAULT true",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_reports BOOLEAN DEFAULT true",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_updates BOOLEAN DEFAULT false",
                "ALTER TABLE screens ADD COLUMN IF NOT EXISTS group_name VARCHAR(255)",
                "ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS layout_type VARCHAR(50) DEFAULT 'SINGLE'",
                "ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS split_rows INT DEFAULT 1",
                "ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS split_cols INT DEFAULT 1",
                "ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS zone_config_json TEXT"
            )
            for (sql in statements) {
                try {
                    jdbcTemplate.execute(sql)
                } catch (e: Exception) {
                    println("Migration statement notice: $sql - ${e.message}")
                }
            }
            
            val encoder = BCryptPasswordEncoder()
            
            // 1. Seed or update admin@pixl.com (Admin Panel) -> admin123
            val adminPixlHash = encoder.encode("admin123")
            val adminPixlCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'admin@pixl.com'", Int::class.java) ?: 0
            if (adminPixlCount == 0) {
                jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('admin@pixl.com', 'Admin PixL', '$adminPixlHash', 'ROLE_ADMIN', 'light', 'UTC', true, true, false)")
                println("Created admin@pixl.com")
            } else {
                jdbcTemplate.execute("UPDATE users SET password_hash = '$adminPixlHash', role = 'ROLE_ADMIN' WHERE email = 'admin@pixl.com'")
                println("Updated admin@pixl.com password and role")
            }

            // 2. Seed or update master@pixl.com (Master Panel) -> master123
            val masterPixlHash = encoder.encode("master123")
            val masterPixlCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'master@pixl.com'", Int::class.java) ?: 0
            if (masterPixlCount == 0) {
                jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('master@pixl.com', 'Master PixL', '$masterPixlHash', 'ROLE_MASTER', 'light', 'UTC', true, true, false)")
                println("Created master@pixl.com")
            } else {
                jdbcTemplate.execute("UPDATE users SET password_hash = '$masterPixlHash', role = 'ROLE_MASTER' WHERE email = 'master@pixl.com'")
                println("Updated master@pixl.com password and role")
            }

            // 3. Keep fallback pixladmin@pixl.com -> admin
            val pixlHash = encoder.encode("admin")
            val pixlCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'pixladmin@pixl.com'", Int::class.java) ?: 0
            if (pixlCount == 0) {
                jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('pixladmin@pixl.com', 'Admin User', '$pixlHash', 'ROLE_MASTER', 'light', 'UTC', true, true, false)")
            } else {
                jdbcTemplate.execute("UPDATE users SET password_hash = '$pixlHash', role = 'ROLE_MASTER' WHERE email = 'pixladmin@pixl.com'")
            }

            println("Database initialization and credential seeding completed successfully.")
        } catch (e: Exception) {
            println("DatabaseInitializer error: ${e.message}")
        }
    }
}

