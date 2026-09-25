package com.viewo.backend.controller

import com.viewo.backend.dto.MessageResponse
import com.viewo.backend.dto.UpdatePasswordRequest
import com.viewo.backend.dto.UpdateProfileRequest
import com.viewo.backend.dto.UserProfileResponse
import com.viewo.backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private fun getCurrentUserEmail(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("Unauthenticated")
        val principal = authentication.principal
        return if (principal is UserDetails) {
            principal.username
        } else {
            principal.toString()
        }
    }

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<*> {
        val email = getCurrentUserEmail()
        val user = userRepository.findByEmail(email).orElse(null)
            ?: return ResponseEntity.badRequest().body(MessageResponse("User not found"))

        return ResponseEntity.ok(
            UserProfileResponse(
                id = user.id,
                email = user.email,
                name = user.name ?: "",
                role = user.role.name,
                theme = user.theme ?: "light",
                timezone = user.timezone ?: "UTC",
                notifyAlerts = user.notifyAlerts ?: true,
                notifyReports = user.notifyReports ?: true,
                notifyUpdates = user.notifyUpdates ?: false
            )
        )
    }

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: UpdateProfileRequest): ResponseEntity<*> {
        val email = getCurrentUserEmail()
        val user = userRepository.findByEmail(email).orElse(null)
            ?: return ResponseEntity.badRequest().body(MessageResponse("User not found"))

        if (request.name != null) user.name = request.name
        if (request.theme != null) user.theme = request.theme
        if (request.timezone != null) user.timezone = request.timezone
        if (request.notifyAlerts != null) user.notifyAlerts = request.notifyAlerts
        if (request.notifyReports != null) user.notifyReports = request.notifyReports
        if (request.notifyUpdates != null) user.notifyUpdates = request.notifyUpdates

        userRepository.save(user)
        return ResponseEntity.ok(MessageResponse("Profile updated successfully"))
    }

    @PutMapping("/password")
    fun updatePassword(@RequestBody request: UpdatePasswordRequest): ResponseEntity<*> {
        val email = getCurrentUserEmail()
        val user = userRepository.findByEmail(email).orElse(null)
            ?: return ResponseEntity.badRequest().body(MessageResponse("User not found"))

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            return ResponseEntity.badRequest().body(MessageResponse("Incorrect current password"))
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword) ?: ""
        userRepository.save(user)
        
        return ResponseEntity.ok(MessageResponse("Password updated successfully"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<*> {
        e.printStackTrace()
        return ResponseEntity.status(500).body(mapOf(
            "error" to e.javaClass.simpleName,
            "message" to e.message,
            "cause" to e.cause?.message,
            "stackTrace" to e.stackTraceToString()
        ))
    }
}
