package com.pixl.backend.controller

import com.pixl.backend.model.Notification
import com.pixl.backend.repository.NotificationRepository
import com.pixl.backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getNotifications(): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userDetails = authentication?.principal as? UserDetails
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

        val user = userRepository.findByEmail(userDetails.username).orElse(null)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "User not found"))

        val notifications = if (user.role.name == "ROLE_MASTER") {
            notificationRepository.findByTargetRoleOrderByCreatedAtDesc("ROLE_MASTER")
        } else {
            // Admin only gets notifications targeting them specifically
            notificationRepository.findByTargetRoleAndTargetUserIdOrderByCreatedAtDesc("ROLE_ADMIN", user.id)
        }

        val res = notifications.map { notif ->
            mapOf(
                "id" to notif.id,
                "message" to notif.message,
                "type" to notif.type,
                "isRead" to notif.isRead,
                "screenId" to notif.screenId,
                "createdAt" to notif.createdAt.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }

        return ResponseEntity.ok(res)
    }

    @PutMapping("/{id}/read")
    fun markAsRead(@PathVariable id: Long): ResponseEntity<*> {
        val notification = notificationRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        notification.isRead = true
        notificationRepository.save(notification)
        
        return ResponseEntity.ok(mapOf("message" to "Notification marked as read"))
    }

    @PutMapping("/read-all")
    fun markAllAsRead(): ResponseEntity<*> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userDetails = authentication?.principal as? UserDetails
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

        val user = userRepository.findByEmail(userDetails.username).orElse(null)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "User not found"))

        val notifications = if (user.role.name == "ROLE_MASTER") {
            notificationRepository.findByTargetRoleOrderByCreatedAtDesc("ROLE_MASTER")
        } else {
            notificationRepository.findByTargetRoleAndTargetUserIdOrderByCreatedAtDesc("ROLE_ADMIN", user.id)
        }

        notifications.forEach { it.isRead = true }
        notificationRepository.saveAll(notifications)

        return ResponseEntity.ok(mapOf("message" to "All notifications marked as read"))
    }
}

