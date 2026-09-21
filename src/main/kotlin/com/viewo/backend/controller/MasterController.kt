package com.viewo.backend.controller

import com.viewo.backend.model.Screen
import com.viewo.backend.repository.ScreenRepository
import com.viewo.backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

import com.viewo.backend.repository.CampaignRepository
import com.viewo.backend.repository.ProofOfPlayRepository
import com.viewo.backend.model.Notification
import com.viewo.backend.repository.NotificationRepository
import com.viewo.backend.repository.PlaylistRepository
import com.viewo.backend.repository.MediaRepository

data class PairScreenRequest(val pairingCode: String, val adminEmail: String)

@RestController
@RequestMapping("/api/master")
class MasterController(
    private val screenRepository: ScreenRepository,
    private val userRepository: UserRepository,
    private val campaignRepository: CampaignRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val notificationRepository: NotificationRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository
) {
    @GetMapping("/stats")
    fun getMasterStats(): ResponseEntity<*> {
        val totalAdmins = userRepository.findAll().count { it.role.name == "ROLE_ADMIN" }
        val screens = screenRepository.findAll()
        val totalScreens = screens.size
        val activeScreens = screens.count { it.status == "ONLINE" || it.status == "ACTIVE" }
        val totalCampaigns = campaignRepository.findAll().size

        return ResponseEntity.ok(mapOf(
            "totalAdmins" to totalAdmins,
            "totalScreens" to totalScreens,
            "activeScreens" to activeScreens,
            "totalCampaigns" to totalCampaigns
        ))
    }

    @GetMapping("/screens")
    fun getAllScreens(): ResponseEntity<*> {
        val screens = screenRepository.findAll()
        val response = screens.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "location" to it.location,
                "pairingCode" to it.pairingCode,
                "status" to it.status,
                "assignedAdmin" to (it.assignedAdmin?.email ?: "Unassigned")
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/admins")
    fun getAdmins(): ResponseEntity<*> {
        val admins = userRepository.findAll().filter { it.role.name == "ROLE_ADMIN" }
        val response = admins.map { 
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "email" to it.email,
                "role" to "Admin"
            )
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/pair-screen")
    fun pairScreen(@RequestBody request: PairScreenRequest): ResponseEntity<*> {
        val adminUser = userRepository.findByEmail(request.adminEmail).orElse(null)
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Admin with this email not found"))

        val screens = screenRepository.findAll().filter { it.pairingCode == request.pairingCode }
        if (screens.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Invalid pairing code. Ensure the screen is turned on and displaying the code."))
        }
        val existingScreen = screens.first()

        existingScreen.status = "ONLINE"
        existingScreen.assignedAdmin = adminUser

        screenRepository.save(existingScreen)

        // Create notification for Admin
        notificationRepository.save(Notification(
            message = "A new screen '${existingScreen.name}' has been paired to your account.",
            type = "INFO",
            targetRole = "ROLE_ADMIN",
            targetUser = adminUser,
            screenId = existingScreen.id
        ))

        // Create notification for Master
        notificationRepository.save(Notification(
            message = "Screen '${existingScreen.name}' was successfully paired and assigned to ${adminUser.email}.",
            type = "SUCCESS",
            targetRole = "ROLE_MASTER",
            screenId = existingScreen.id
        ))

        return ResponseEntity.ok(mapOf("message" to "Screen paired and assigned to Admin successfully!"))
    }

    @GetMapping("/proof-of-play")
    fun getMasterProofOfPlay(): ResponseEntity<*> {
        val logs = proofOfPlayRepository.findAll()
        val response = logs.map { log ->
            mapOf(
                "id" to log.id,
                "screenName" to log.screen.name,
                "adminEmail" to (log.screen.assignedAdmin?.email ?: "Unassigned"),
                "mediaUrl" to log.media.publicUrl,
                "mediaType" to log.media.type,
                "playedAt" to log.playedAt.format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
            )
        }.sortedByDescending { it["playedAt"] as String }

        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/screens/{id}")
    fun deleteScreen(@PathVariable id: Long): ResponseEntity<*> {
        val screen = screenRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
            
        // Delete all associated proof of play logs first
        proofOfPlayRepository.deleteByScreenId(id)
        
        // Remove the screen from any target campaigns
        val campaigns = campaignRepository.findAll()
        for (campaign in campaigns) {
            if (campaign.targetScreens.any { it.id == id }) {
                campaign.targetScreens.removeIf { it.id == id }
                campaignRepository.save(campaign)
            }
        }
        
        screenRepository.delete(screen)

        // Notification to Master
        notificationRepository.save(Notification(
            message = "Screen '${screen.name}' was deleted.",
            type = "WARNING",
            targetRole = "ROLE_MASTER",
            screenId = id
        ))

        // Notification to Admin (if it was assigned)
        if (screen.assignedAdmin != null) {
            notificationRepository.save(Notification(
                message = "Screen '${screen.name}' assigned to you has been deleted by Master.",
                type = "ERROR",
                targetRole = "ROLE_ADMIN",
                targetUser = screen.assignedAdmin,
                screenId = id
            ))
        }

        return ResponseEntity.ok(mapOf("message" to "Screen deleted successfully"))
    }

    @DeleteMapping("/admins/{id}")
    fun deleteAdmin(@PathVariable id: Long): ResponseEntity<*> {
        val admin = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        if (admin.role.name != "ROLE_ADMIN") {
            return ResponseEntity.badRequest().body(mapOf("error" to "Can only delete Admin users"))
        }

        // Unassign any screens from this admin
        val screens = screenRepository.findByAssignedAdminId(id)
        for (screen in screens) {
            screen.assignedAdmin = null
            screenRepository.save(screen)
        }
        
        // Remove admin from their campaigns
        val campaigns = campaignRepository.findAll().filter { it.creator?.id == id }
        campaigns.forEach { it.creator = null; campaignRepository.save(it) }

        // Remove admin from their playlists
        val playlists = playlistRepository.findAll().filter { it.creator?.id == id }
        playlists.forEach { it.creator = null; playlistRepository.save(it) }

        // Remove admin from their media
        val media = mediaRepository.findAll().filter { it.uploader?.id == id }
        media.forEach { it.uploader = null; mediaRepository.save(it) }

        // Delete any notifications targeted to this admin
        val notifications = notificationRepository.findAll().filter { it.targetUser?.id == id }
        notificationRepository.deleteAll(notifications)
        
        // Delete user
        try {
            userRepository.delete(admin)
            return ResponseEntity.ok(mapOf("message" to "Admin deleted successfully"))
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to delete admin due to an unexpected constraint. Check server logs."))
        }
    }
}
