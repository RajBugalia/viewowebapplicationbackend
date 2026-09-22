package com.viewo.backend.controller

import com.viewo.backend.repository.ScreenRepository
import com.viewo.backend.security.services.UserDetailsImpl
import com.viewo.backend.repository.ProofOfPlayRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter
import com.viewo.backend.model.Notification
import com.viewo.backend.repository.NotificationRepository

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val screenRepository: ScreenRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val campaignRepository: com.viewo.backend.repository.CampaignRepository,
    private val notificationRepository: NotificationRepository
) {
    @GetMapping("/dashboard")
    fun getAdminDashboard(): ResponseEntity<*> {
        return ResponseEntity.ok(mapOf("message" to "Welcome to the Admin Panel Dashboard!"))
    }

    @GetMapping("/screens")
    fun getAdminScreens(): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val screens = screenRepository.findByAssignedAdminId(adminId)
        val response = screens.map { screen ->
            mapOf(
                "id" to screen.id,
                "name" to screen.name,
                "location" to screen.location,
                "status" to screen.status,
                "pairingCode" to screen.pairingCode
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/proof-of-play")
    fun getAdminProofOfPlay(): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val logs = proofOfPlayRepository.findByScreenAssignedAdminId(adminId)
        val response = logs.map { log ->
            mapOf(
                "id" to log.id,
                "screenName" to log.screen.name,
                "campaignName" to (log.campaign?.name ?: "Unknown Campaign"),
                "mediaName" to log.media.name,
                "mediaUrl" to log.media.publicUrl,
                "mediaType" to log.media.type,
                "duration" to (log.duration ?: 0),
                "status" to (log.status ?: "UNKNOWN"),
                "playedAt" to log.playedAt.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }.sortedByDescending { it["playedAt"] as String }

        return ResponseEntity.ok(response)
    }

    @org.springframework.web.bind.annotation.PutMapping("/screens/{id}")
    fun updateScreen(@org.springframework.web.bind.annotation.PathVariable id: Long, @org.springframework.web.bind.annotation.RequestBody request: Map<String, String>): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val screen = screenRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        // Ensure the screen belongs to this admin
        if (screen.assignedAdmin?.id != adminId) {
            return ResponseEntity.status(403).body(mapOf("error" to "Forbidden: Screen does not belong to you"))
        }
        
        val newName = request["name"]
        if (newName.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name cannot be empty"))
        }

        screen.name = newName
        screenRepository.save(screen)

        // Notification to Admin
        notificationRepository.save(Notification(
            message = "Screen name updated to '${screen.name}'.",
            type = "INFO",
            targetRole = "ROLE_ADMIN",
            targetUser = screen.assignedAdmin,
            screenId = id
        ))

        return ResponseEntity.ok(mapOf("message" to "Screen updated successfully", "name" to screen.name))
    }
}
