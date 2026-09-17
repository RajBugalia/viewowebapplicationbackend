package com.viewo.backend.controller

import com.viewo.backend.model.Screen
import com.viewo.backend.model.ProofOfPlayLog
import com.viewo.backend.repository.ScreenRepository
import com.viewo.backend.repository.CampaignRepository
import com.viewo.backend.repository.MediaRepository
import com.viewo.backend.repository.ProofOfPlayRepository
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/tv")
class ScreenApiController(
    private val screenRepository: ScreenRepository,
    private val campaignRepository: CampaignRepository,
    private val mediaRepository: MediaRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    data class RegisterRequest(val pairingCode: String, val screenId: Long? = null)

    // 1. Register a new screen or update pairing code for existing screen
    @PostMapping("/register")
    fun registerScreen(@RequestBody request: RegisterRequest): ResponseEntity<*> {
        val code = request.pairingCode
        
        var screen = request.screenId?.let { screenRepository.findById(it).orElse(null) }
        
        if (screen != null) {
            // Update existing screen's pairing code if it is offline/unpaired
            if (screen.status == "OFFLINE") {
                val updatedScreen = screenRepository.save(screen.copy(pairingCode = code))
                return ResponseEntity.ok(mapOf(
                    "pairingCode" to updatedScreen.pairingCode,
                    "screenId" to updatedScreen.id
                ))
            } else {
                // If it's already paired, don't change the code, just return it
                return ResponseEntity.ok(mapOf(
                    "pairingCode" to screen.pairingCode,
                    "screenId" to screen.id
                ))
            }
        } else {
            // Create a new screen
            val newScreen = Screen(
                name = "New TV Display",
                location = "Unassigned",
                pairingCode = code,
                status = "OFFLINE"
            )
            val saved = screenRepository.save(newScreen)
            
            return ResponseEntity.ok(mapOf(
                "pairingCode" to saved.pairingCode,
                "screenId" to saved.id
            ))
        }
    }

    // 2. Poll for campaign updates
    @GetMapping("/{screenId}/poll")
    fun pollScreen(@PathVariable screenId: Long): ResponseEntity<*> {
        val screen = screenRepository.findById(screenId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        // Check if paired (i.e. status is ONLINE and assignedAdmin is not null)
        if (screen.status == "OFFLINE" || screen.assignedAdmin == null) {
            return ResponseEntity.ok(mapOf("status" to "UNPAIRED", "activeCampaign" to null))
        }

        // Find active campaign for this screen
        val now = LocalDateTime.now()
        val campaigns = campaignRepository.findAll()
        val activeCampaign = campaigns.firstOrNull { campaign ->
            campaign.targetScreens.any { it.id == screen.id } &&
            now.isAfter(campaign.startDate) && now.isBefore(campaign.endDate)
        }

        if (activeCampaign == null) {
            return ResponseEntity.ok(mapOf("status" to "PAIRED_NO_CAMPAIGN", "activeCampaign" to null))
        }

        // Construct DTO
        val campaignDto = mapOf(
            "id" to activeCampaign.id,
            "name" to activeCampaign.name,
            "playlist" to mapOf(
                "id" to activeCampaign.playlist.id,
                "name" to activeCampaign.playlist.name,
                "mediaItems" to activeCampaign.playlist.mediaItems.map { media ->
                    mapOf(
                        "id" to media.id,
                        "publicUrl" to media.publicUrl,
                        "type" to media.type,
                        "durationSeconds" to media.durationSeconds
                    )
                }
            )
        )

        return ResponseEntity.ok(mapOf(
            "status" to "ACTIVE",
            "activeCampaign" to campaignDto
        ))
    }

    // 3. Receive Proof of Play logs
    @PostMapping("/{screenId}/proof-of-play")
    fun uploadProofOfPlay(@PathVariable screenId: Long, @RequestBody logs: List<Map<String, Any>>): ResponseEntity<*> {
        val screen = screenRepository.findById(screenId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        val entitiesToSave = mutableListOf<ProofOfPlayLog>()
        for (log in logs) {
            val mediaId = (log["mediaId"] as? Number)?.toLong() ?: continue
            val timestampMs = (log["timestamp"] as? Number)?.toLong() ?: continue
            
            val media = mediaRepository.findById(mediaId).orElse(null) ?: continue
            
            val playedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault())
            entitiesToSave.add(ProofOfPlayLog(screen = screen, media = media, playedAt = playedAt))
        }

        proofOfPlayRepository.saveAll(entitiesToSave)
        return ResponseEntity.ok(mapOf("status" to "ok", "saved" to entitiesToSave.size))
    }

    @GetMapping("/debug-db")
    fun debugDb(): ResponseEntity<*> {
        return try {
            val tables = jdbcTemplate.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public';", String::class.java)
            ResponseEntity.ok(mapOf("tables" to tables))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to e.message))
        }
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
