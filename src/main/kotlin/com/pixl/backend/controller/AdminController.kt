package com.pixl.backend.controller

import com.pixl.backend.repository.ScreenRepository
import com.pixl.backend.security.services.UserDetailsImpl
import com.pixl.backend.repository.ProofOfPlayRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter
import com.pixl.backend.model.Notification
import com.pixl.backend.repository.NotificationRepository

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val screenRepository: ScreenRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val campaignRepository: com.pixl.backend.repository.CampaignRepository,
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
                "groupName" to (screen.groupName ?: ""),
                "status" to screen.status,
                "pairingCode" to screen.pairingCode
            )
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/groups")
    fun getAdminGroups(): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val screens = screenRepository.findByAssignedAdminId(adminId)
        val groups = screens.mapNotNull { it.groupName }
            .filter { it.isNotBlank() }
            .distinct()
            .map { gName ->
                mapOf(
                    "name" to gName,
                    "screenCount" to screens.count { it.groupName == gName }
                )
            }
        return ResponseEntity.ok(groups)
    }

    @org.springframework.web.bind.annotation.PutMapping("/screens/{id}/group")
    fun updateScreenGroup(
        @org.springframework.web.bind.annotation.PathVariable id: Long, 
        @org.springframework.web.bind.annotation.RequestBody request: Map<String, String?>
    ): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val screen = screenRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        if (screen.assignedAdmin?.id != adminId) {
            return ResponseEntity.status(403).body(mapOf("error" to "Forbidden: Screen does not belong to you"))
        }

        val rawGroup = request["groupName"]?.trim()
        screen.groupName = if (rawGroup.isNullOrBlank()) null else rawGroup
        val saved = screenRepository.save(screen)

        return ResponseEntity.ok(mapOf(
            "message" to "Screen group updated successfully",
            "screenId" to saved.id,
            "groupName" to (saved.groupName ?: "")
        ))
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/groups/{groupName}")
    fun deleteGroup(@org.springframework.web.bind.annotation.PathVariable groupName: String): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val userDetails = authentication.principal as UserDetailsImpl
        val adminId = userDetails.id

        val screens = screenRepository.findByAssignedAdminId(adminId).filter { it.groupName == groupName }
        screens.forEach {
            it.groupName = null
            screenRepository.save(it)
        }
        return ResponseEntity.ok(mapOf("message" to "Group '$groupName' deleted. Screens are now ungrouped."))
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
                "mediaName" to log.media.filename,
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
        
        // Disallow editing screen name for admin
        if (request.containsKey("name")) {
            return ResponseEntity.status(403).body(mapOf("error" to "Admins are not permitted to change the screen name."))
        }

        if (request.containsKey("location")) {
            screen.location = request["location"] ?: screen.location
        }

        if (request.containsKey("groupName")) {
            val grp = request["groupName"]?.trim()
            screen.groupName = if (grp.isNullOrBlank()) null else grp
        }

        screenRepository.save(screen)

        return ResponseEntity.ok(mapOf("message" to "Screen updated successfully", "screenId" to screen.id))
    }
}

