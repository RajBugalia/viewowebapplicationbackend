package com.viewo.backend.controller

import com.viewo.backend.model.Media
import com.viewo.backend.repository.MediaRepository
import com.viewo.backend.repository.UserRepository
import com.viewo.backend.security.services.UserDetailsImpl
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@RestController
@RequestMapping("/api/media")
class MediaController(
    private val s3Client: S3Client,
    private val mediaRepository: MediaRepository,
    private val userRepository: UserRepository,
    private val playlistRepository: com.viewo.backend.repository.PlaylistRepository,
    private val proofOfPlayRepository: com.viewo.backend.repository.ProofOfPlayRepository,
    @Value("\${do.spaces.bucket}") private val bucket: String,
    @Value("\${do.spaces.endpoint}") private val endpoint: String,
    @Value("\${do.spaces.access-key}") private val accessKey: String
) {

    @PostMapping("/upload")
    fun uploadMedia(@RequestParam("file") file: MultipartFile): ResponseEntity<*> {
        if (accessKey == "YOUR_ACCESS_KEY") {
            return ResponseEntity.badRequest().body(mapOf("error" to "Storage is not configured yet. Please update application.properties"))
        }

        val userDetails = SecurityContextHolder.getContext().authentication?.principal as? UserDetailsImpl
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val user = userRepository.findById(userDetails.id).orElseThrow()

        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "")
        val key = "media/${UUID.randomUUID()}.$extension"

        return try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.contentType)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build()

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.inputStream, file.size))

            // DO Spaces URL format: https://bucket.sfo3.digitaloceanspaces.com/key
            val regionStr = endpoint.replace("https://", "")
            val cdnRegionStr = regionStr.replace(".digitaloceanspaces.com", ".cdn.digitaloceanspaces.com")
            val publicUrl = "https://$bucket.$cdnRegionStr/$key"

            val type = if (file.contentType?.startsWith("video") == true) "VIDEO" else "IMAGE"

            val media = Media(
                filename = originalFilename,
                publicUrl = publicUrl,
                type = type,
                uploader = user
            )

            mediaRepository.save(media)

            ResponseEntity.ok(media)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Upload failed: ${e.message}"))
        }
    }
    
    @GetMapping
    fun getMyMedia(): ResponseEntity<*> {
        val userDetails = SecurityContextHolder.getContext().authentication?.principal as? UserDetailsImpl
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))
            
        val mediaList = mediaRepository.findByUploaderId(userDetails.id)
        return ResponseEntity.ok(mediaList)
    }

    @DeleteMapping("/{id}")
    fun deleteMedia(@PathVariable id: Long): ResponseEntity<*> {
        val userDetails = SecurityContextHolder.getContext().authentication?.principal as? UserDetailsImpl
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

        val media = mediaRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        if (media.uploader.id != userDetails.id) {
            // Check if master admin? Actually only uploader can delete for now
            val user = userRepository.findById(userDetails.id).orElseThrow()
            if (user.role.name != "ROLE_MASTER") {
                return ResponseEntity.status(403).body(mapOf("error" to "Forbidden"))
            }
        }

        // Delete proof of play logs first
        proofOfPlayRepository.deleteByMediaId(id)

        // Remove media from any playlists
        val playlists = playlistRepository.findAll()
        for (playlist in playlists) {
            if (playlist.mediaItems.any { it.id == id }) {
                playlist.mediaItems.removeIf { it.id == id }
                playlistRepository.save(playlist)
            }
        }

        // Attempt to delete from DO Spaces
        try {
            val key = media.publicUrl.substringAfterLast("com/")
            s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())
        } catch (e: Exception) {
            println("FAILED to delete from DO Spaces: ${e.message}")
            e.printStackTrace()
            // Ignore if DO Spaces fails, still delete from DB
        }

        mediaRepository.delete(media)
        return ResponseEntity.ok(mapOf("message" to "Media deleted successfully"))
    }
}
