package com.pixl.backend.repository

import com.pixl.backend.model.Playlist
import org.springframework.data.jpa.repository.JpaRepository

interface PlaylistRepository : JpaRepository<Playlist, Long>

