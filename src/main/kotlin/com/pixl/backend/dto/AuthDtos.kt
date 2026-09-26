package com.pixl.backend.dto

data class LoginRequest(val email: String, val passwordHash: String)
data class SignupRequest(val name: String, val email: String, val passwordHash: String, val role: String)
data class JwtResponse(val token: String, val id: Long, val email: String, val name: String, val role: String)
data class MessageResponse(val message: String)

data class UpdateProfileRequest(
    val name: String?,
    val theme: String?,
    val timezone: String?,
    val notifyAlerts: Boolean?,
    val notifyReports: Boolean?,
    val notifyUpdates: Boolean?
)

data class UpdatePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String? = "",
    val role: String,
    val theme: String? = "light",
    val timezone: String? = "UTC",
    val notifyAlerts: Boolean? = true,
    val notifyReports: Boolean? = true,
    val notifyUpdates: Boolean? = false
)

