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

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val screenRepository: ScreenRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val campaignRepository: com.viewo.backend.repository.CampaignRepository
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
                "mediaUrl" to log.media.publicUrl,
                "mediaType" to log.media.type,
                "playedAt" to log.playedAt.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }.sortedByDescending { it["playedAt"] as String }

        return ResponseEntity.ok(response)
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/screens/{id}")
    fun deleteScreen(@org.springframework.web.bind.annotation.PathVariable id: Long): ResponseEntity<*> {
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
        
        // Delete associated logs
        proofOfPlayRepository.deleteByScreenId(id)
        
        // Remove from campaigns
        val campaigns = campaignRepository.findAll()
        for (campaign in campaigns) {
            if (campaign.targetScreens.any { it.id == id }) {
                campaign.targetScreens.removeIf { it.id == id }
                campaignRepository.save(campaign)
            }
        }
        
        screenRepository.delete(screen)
        return ResponseEntity.ok(mapOf("message" to "Screen deleted successfully"))
    }
}
