package com.viewo.backend.dto

data class LoginRequest(val email: String, val passwordHash: String)
data class SignupRequest(val name: String, val email: String, val passwordHash: String, val role: String)
data class JwtResponse(val token: String, val id: Long, val email: String, val name: String, val role: String)
data class MessageResponse(val message: String)
