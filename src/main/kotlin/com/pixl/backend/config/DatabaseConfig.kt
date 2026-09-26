package com.pixl.backend.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    @Value("\${DATABASE_URL:}")
    lateinit var databaseUrl: String

    @Value("\${local.datasource.url:jdbc:postgresql://localhost:5432/pixl}")
    lateinit var localUrl: String

    @Value("\${local.datasource.username:postgres}")
    lateinit var localUsername: String

    @Value("\${local.datasource.password:postgres}")
    lateinit var localPassword: String

    @Bean
    fun dataSource(): DataSource {
        val basicDataSource = HikariDataSource()
        
        // DigitalOcean injects database URLs with prefixes like DEV_DB_790496_DATABASE_URL
        val env = System.getenv()
        var doDatabaseUrl = env["DATABASE_URL"]
        
        if (doDatabaseUrl == null || !doDatabaseUrl.startsWith("postgres")) {
            for ((key, value) in env) {
                if (key.endsWith("_DATABASE_URL") && value.startsWith("postgres")) {
                    doDatabaseUrl = value
                    break
                }
            }
        }
        
        if (doDatabaseUrl != null && doDatabaseUrl.startsWith("postgres")) {
            val dbUri = URI(doDatabaseUrl)
            val username = dbUri.userInfo.split(":")[0]
            val password = dbUri.userInfo.split(":")[1]
            
            // Convert to JDBC format expected by Spring/Hibernate
            val dbUrl = "jdbc:postgresql://${dbUri.host}:${dbUri.port}${dbUri.path}?sslmode=require"
            
            basicDataSource.jdbcUrl = dbUrl
            basicDataSource.username = username
            basicDataSource.password = password
        } else {
            // Fallback to local development credentials
            basicDataSource.jdbcUrl = localUrl
            basicDataSource.username = localUsername
            basicDataSource.password = localPassword
        }
        
        return basicDataSource
    }
}

