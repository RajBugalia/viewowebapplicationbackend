package com.pixl.backend.repository

import com.pixl.backend.model.Media
import org.springframework.data.jpa.repository.JpaRepository

interface MediaRepository : JpaRepository<Media, Long> {
    fun findByUploaderId(uploaderId: Long): List<Media>
}

