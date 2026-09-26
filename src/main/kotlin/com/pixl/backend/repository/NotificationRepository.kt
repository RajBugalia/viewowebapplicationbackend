package com.pixl.backend.repository

import com.pixl.backend.model.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByTargetRoleOrderByCreatedAtDesc(role: String): List<Notification>
    fun findByTargetRoleAndTargetUserIdOrderByCreatedAtDesc(role: String, userId: Long): List<Notification>
}

