package com.viewo.backend.config

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
                    println("Migration statement executed with notice: $sql - ${e.message}")
                }
            }
            
            // Ensure default admin exists and has password 'admin'
            val encoder = BCryptPasswordEncoder()
            val hash = encoder.encode("admin")
            val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'admin@viewo.com'", Int::class.java) ?: 0
            if (count == 0) {
                jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('admin@viewo.com', 'Admin User', '$hash', 'ROLE_MASTER', 'light', 'UTC', true, true, false)")
                println("Created default admin user (admin@viewo.com / admin).")
            } else {
                jdbcTemplate.execute("UPDATE users SET password_hash = '$hash', role = 'ROLE_MASTER' WHERE email = 'admin@viewo.com'")
                println("Updated default admin user password to 'admin'.")
            }
        } catch (e: Exception) {
            println("DatabaseInitializer failed: ${e.message}")
        }
    }
}
