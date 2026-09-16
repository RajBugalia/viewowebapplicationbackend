package com.viewo.backend.repository

import com.viewo.backend.model.Playlist
import org.springframework.data.jpa.repository.JpaRepository

interface PlaylistRepository : JpaRepository<Playlist, Long>
