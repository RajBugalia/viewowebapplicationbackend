package com.pixl.backend.security.jwt

import com.pixl.backend.security.services.UserDetailsImpl
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtils {

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${jwt.expirationMs}")
    private var jwtExpirationMs: Int = 0

    private fun key(): SecretKey {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))
    }

    fun generateJwtToken(authentication: Authentication): String {
        val userPrincipal = authentication.principal as UserDetailsImpl
        return Jwts.builder()
            .subject(userPrincipal.username)
            .issuedAt(Date())
            .expiration(Date(Date().time + jwtExpirationMs))
            .signWith(key())
            .compact()
    }

    fun getUserNameFromJwtToken(token: String): String {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).payload.subject
    }

    fun validateJwtToken(authToken: String): Boolean {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

