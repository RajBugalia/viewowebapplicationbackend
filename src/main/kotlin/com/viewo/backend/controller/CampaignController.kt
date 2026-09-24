package com.viewo.backend.controller

import com.viewo.backend.dto.CreateCampaignRequest
import com.viewo.backend.model.Campaign
import com.viewo.backend.repository.CampaignRepository
import com.viewo.backend.repository.PlaylistRepository
import com.viewo.backend.repository.ScreenRepository
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.security.Principal
import com.viewo.backend.repository.UserRepository

@RestController
@RequestMapping("/api/campaigns")
class CampaignController(
    private val campaignRepository: CampaignRepository,
    private val playlistRepository: PlaylistRepository,
    private val screenRepository: ScreenRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository
) {
    private fun parseDateTime(dateStr: String): Instant {
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(dateStr).toInstant()
            } catch (e2: Exception) {
                LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant()
            }
        }
    }

    private fun serializeZones(zones: List<com.viewo.backend.dto.ZoneConfigDto>?): String? {
        if (zones.isNullOrEmpty()) return null
        return "[" + zones.joinToString(",") { z ->
            """{"zoneIndex":${z.zoneIndex},"row":${z.row},"col":${z.col},"playlistId":${z.playlistId}}"""
        } + "]"
    }

    @GetMapping
    fun getAllCampaigns(principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
        val campaigns = campaignRepository.findAll().filter { it.creator?.id == user?.id }
        val response = campaigns.map { c ->
            mapOf(
                "id" to c.id,
                "name" to c.name,
                "startDate" to c.startDate.toString(),
                "endDate" to c.endDate.toString(),
                "layoutType" to c.layoutType,
                "splitRows" to c.splitRows,
                "splitCols" to c.splitCols,
                "zoneConfigJson" to (c.zoneConfigJson ?: ""),
                "playlist" to (c.playlist?.let {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name
                    )
                }),
                "targetScreens" to c.targetScreens.map { s ->
                    mapOf(
                        "id" to s.id,
                        "name" to s.name,
                        "pairingCode" to s.pairingCode
                    )
                }
            )
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createCampaign(@RequestBody request: CreateCampaignRequest, principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
        val isSplit = request.layoutType.equals("SPLIT", ignoreCase = true)

        val playlist = if (request.playlistId != null) {
            playlistRepository.findById(request.playlistId).orElse(null)
        } else if (isSplit && !request.zones.isNullOrEmpty()) {
            playlistRepository.findById(request.zones[0].playlistId).orElse(null)
        } else {
            null
        }

        if (playlist == null && !isSplit) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Playlist not found"))
        }
            
        val screens = screenRepository.findAllById(request.targetScreenIds).toMutableList()
        if (screens.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "No valid screens selected or found"))
        }
        
        val campaign = Campaign(
            name = request.name,
            startDate = parseDateTime(request.startDate),
            endDate = parseDateTime(request.endDate),
            playlist = playlist,
            layoutType = if (isSplit) "SPLIT" else "SINGLE",
            splitRows = if (isSplit) (request.splitRows ?: 1) else 1,
            splitCols = if (isSplit) (request.splitCols ?: 1) else 1,
            zoneConfigJson = if (isSplit) serializeZones(request.zones) else null,
            creator = user
        )
        campaign.targetScreens.addAll(screens)
        
        println("Saving campaign '${campaign.name}' with layout ${campaign.layoutType} and ${campaign.targetScreens.size} screens")
        
        val saved = campaignRepository.save(campaign)
        
        // Broadcast a refresh event to each assigned screen over WebSocket
        screens.forEach { screen ->
            messagingTemplate.convertAndSend(
                "/topic/screens/${screen.pairingCode}", 
                mapOf(
                    "command" to "REFRESH_CAMPAIGN",
                    "campaignId" to saved.id
                ) as Any
            )
        }
        
        return ResponseEntity.ok(mapOf(
            "id" to saved.id,
            "name" to saved.name,
            "message" to "Campaign created and broadcasted to screens!"
        ))
    }

    @DeleteMapping("/{id}")
    fun deleteCampaign(@PathVariable id: Long): ResponseEntity<*> {
        val campaign = campaignRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
            
        // Broadcast a refresh/stop event to each assigned screen over WebSocket
        campaign.targetScreens.forEach { screen ->
            messagingTemplate.convertAndSend(
                "/topic/screens/${screen.pairingCode}", 
                mapOf("command" to "STOP_CAMPAIGN") as Any
            )
        }
        
        campaignRepository.delete(campaign)
        return ResponseEntity.ok(mapOf("message" to "Campaign deleted successfully"))
    }

    @PutMapping("/{id}")
    fun updateCampaign(@PathVariable id: Long, @RequestBody request: Map<String, String>, principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

        val campaign = campaignRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        if (campaign.creator?.id != user.id) {
            return ResponseEntity.status(403).body(mapOf("error" to "Forbidden: Campaign does not belong to you"))
        }

        val newName = request["name"]
        if (newName.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name cannot be empty"))
        }

        campaign.name = newName
        campaignRepository.save(campaign)

        return ResponseEntity.ok(mapOf("message" to "Campaign updated successfully", "name" to campaign.name))
    }
}
