package com.pixl.backend.controller

import com.pixl.backend.dto.*
import com.pixl.backend.model.Role
import com.pixl.backend.model.User
import com.pixl.backend.repository.UserRepository
import com.pixl.backend.security.jwt.JwtUtils
import com.pixl.backend.security.services.UserDetailsImpl
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils
) {

    @PostMapping("/signin")
    fun authenticateUser(@RequestBody loginRequest: LoginRequest): ResponseEntity<*> {
        try {
            val authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(loginRequest.email ?: "", loginRequest.passwordHash ?: "")
            )

            SecurityContextHolder.getContext().authentication = authentication
            val jwt = jwtUtils.generateJwtToken(authentication)
            
            val userDetails = authentication.principal as UserDetailsImpl
            val role = userDetails.authorities.firstOrNull()?.authority ?: "ROLE_ADMIN"

            return ResponseEntity.ok(JwtResponse(jwt, userDetails.id, userDetails.username, userDetails.name ?: "", role))
        } catch (e: org.springframework.security.core.AuthenticationException) {
            return ResponseEntity.status(401).body(mapOf("message" to "Invalid email or password"))
        } catch (e: Exception) {
            return ResponseEntity.status(500).body(mapOf("message" to (e.message ?: "Authentication error")))
        }
    }

    @PostMapping("/signup")
    fun registerUser(@RequestBody signUpRequest: SignupRequest): ResponseEntity<*> {
        if (userRepository.existsByEmail(signUpRequest.email ?: "")) {
            return ResponseEntity
                .badRequest()
                .body(MessageResponse("Error: Email is already in use!"))
        }

        val parsedRole = if ((signUpRequest.role ?: "").equals("MASTER", ignoreCase = true)) Role.ROLE_MASTER else Role.ROLE_ADMIN

        val user = User(
            name = signUpRequest.name ?: "",
            email = signUpRequest.email ?: "",
            passwordHash = passwordEncoder.encode(signUpRequest.passwordHash ?: "") ?: "",
            role = parsedRole
        )

        userRepository.save(user)

        return ResponseEntity.ok(MessageResponse("User registered successfully!"))
    }
}

