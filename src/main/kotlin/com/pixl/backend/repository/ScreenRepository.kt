package com.pixl.backend.repository

import com.pixl.backend.model.Screen
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ScreenRepository : JpaRepository<Screen, Long> {
    fun findByAssignedAdminId(adminId: Long): List<Screen>
    fun findByPairingCode(pairingCode: String): Optional<Screen>
}

