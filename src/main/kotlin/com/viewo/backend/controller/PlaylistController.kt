package com.viewo.backend.controller

import com.viewo.backend.dto.CreatePlaylistRequest
import com.viewo.backend.model.Playlist
import com.viewo.backend.repository.MediaRepository
import com.viewo.backend.repository.PlaylistRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import com.viewo.backend.repository.UserRepository

@RestController
@RequestMapping("/api/playlists")
class PlaylistController(
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    private val campaignRepository: com.viewo.backend.repository.CampaignRepository,
    private val userRepository: UserRepository
) {
    @GetMapping
    fun getAllPlaylists(principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
        val playlists = playlistRepository.findAll().filter { it.creator?.id == user?.id }
        val response = playlists.map { p ->
            mapOf(
                "id" to p.id,
                "name" to p.name,
                "mediaCount" to p.mediaItems.size,
                "media" to p.mediaItems.map { m ->
                    mapOf(
                        "id" to m.id,
                        "filename" to m.filename,
                        "type" to m.type,
                        "publicUrl" to m.publicUrl
                    )
                }
            )
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createPlaylist(@RequestBody request: CreatePlaylistRequest, principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
        val mediaItems = mediaRepository.findAllById(request.mediaIds)
        
        val playlist = Playlist(
            name = request.name,
            mediaItems = mediaItems,
            creator = user
        )
        
        val saved = playlistRepository.save(playlist)
        
        return ResponseEntity.ok(mapOf(
            "id" to saved.id,
            "name" to saved.name,
            "message" to "Playlist created successfully"
        ))
    }

    @DeleteMapping("/{id}")
    fun deletePlaylist(@PathVariable id: Long): ResponseEntity<*> {
        val playlist = playlistRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
            
        // Delete any campaigns that use this playlist
        val campaigns = campaignRepository.findAll()
        for (campaign in campaigns) {
            val usesInZone = campaign.zoneConfigJson?.contains("\"playlistId\":$id") == true
            if (campaign.playlist?.id == id || usesInZone) {
                campaignRepository.delete(campaign)
            }
        }
            
        playlistRepository.delete(playlist)
        return ResponseEntity.ok(mapOf("message" to "Playlist deleted successfully"))
    }

    @PutMapping("/{id}")
    fun updatePlaylist(@PathVariable id: Long, @RequestBody request: Map<String, String>, principal: Principal): ResponseEntity<*> {
        val user = userRepository.findByEmail(principal.name).orElse(null)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

        val playlist = playlistRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        if (playlist.creator?.id != user.id) {
            return ResponseEntity.status(403).body(mapOf("error" to "Forbidden: Playlist does not belong to you"))
        }

        val newName = request["name"]
        if (newName.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name cannot be empty"))
        }

        playlist.name = newName
        playlistRepository.save(playlist)

        return ResponseEntity.ok(mapOf("message" to "Playlist updated successfully", "name" to playlist.name))
    }
}
