package com.viewo.backend.service

import com.viewo.backend.model.Notification
import com.viewo.backend.repository.NotificationRepository
import com.viewo.backend.repository.ScreenRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class HeartbeatMonitorService(
    private val screenRepository: ScreenRepository,
    private val notificationRepository: NotificationRepository
) {

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    fun checkHeartbeats() {
        val screens = screenRepository.findAll()
        val twoMinutesAgo = LocalDateTime.now().minusMinutes(2)

        for (screen in screens) {
            // Only care about screens that are currently marked ONLINE and have an assigned admin
            if (screen.status == "ONLINE" && screen.assignedAdmin != null) {
                // If it never pinged, or pinged longer than 2 minutes ago
                if (screen.lastPingAt == null || screen.lastPingAt!!.isBefore(twoMinutesAgo)) {
                    screen.status = "OFFLINE"
                    screenRepository.save(screen)

                    // Notify Master
                    notificationRepository.save(Notification(
                        message = "Screen '${screen.name}' has gone OFFLINE (Lost power/internet).",
                        type = "WARNING",
                        targetRole = "ROLE_MASTER",
                        screenId = screen.id
                    ))

                    // Notify assigned Admin
                    notificationRepository.save(Notification(
                        message = "Your screen '${screen.name}' has gone OFFLINE (Lost power/internet).",
                        type = "WARNING",
                        targetRole = "ROLE_ADMIN",
                        targetUser = screen.assignedAdmin,
                        screenId = screen.id
                    ))
                }
            }
        }
    }
}
