package com.pixl.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notifications")
data class Notification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 500)
    var message: String,

    @Column(nullable = false, length = 20)
    var type: String, // e.g., "INFO", "SUCCESS", "WARNING", "ERROR"

    @Column(name = "target_role", nullable = false, length = 50)
    var targetRole: String, // "ROLE_MASTER" or "ROLE_ADMIN"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    var targetUser: User? = null, // Optional: if meant for a specific Admin

    @Column(name = "screen_id")
    var screenId: Long? = null, // Optional: linking to a screen

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

