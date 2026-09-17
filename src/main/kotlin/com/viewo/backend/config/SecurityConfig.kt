package com.viewo.backend.config

import com.viewo.backend.security.jwt.AuthTokenFilter
import com.viewo.backend.security.services.UserDetailsServiceImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: UserDetailsServiceImpl,
    private val authTokenFilter: AuthTokenFilter
) {

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
    
    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()
        config.allowCredentials = true
        config.addAllowedOriginPattern("*") // Allow all for dev
        config.addAllowedHeader("*")
        config.addAllowedMethod("*")
        source.registerCorsConfiguration("/**", config)
        return CorsFilter(source)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { } 
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                    "/api/auth/**",
                    "/api/tv/register",
                    "/api/tv/*/poll",
                    "/api/tv/*/proof-of-play",
                    "/api/tv/init-db",
                    "/api/tv/debug-db",
                    "/api/tv/hash/**",
                    "/api/tv/fix-admin",
                    "/error",
                    "/favicon.ico"
                ).permitAll()
                    .requestMatchers("/api/master/**").hasRole("MASTER")
                    .requestMatchers("/api/admin/**", "/api/playlists/**", "/api/campaigns/**").hasAnyRole("ADMIN", "MASTER")
                    .requestMatchers("/api/media/**").hasAnyRole("ADMIN", "MASTER")
                    .anyRequest().authenticated()
            }
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
}
