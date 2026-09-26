package com.pixl.backend.model

import jakarta.persistence.*

@Entity
@Table(name = "playlists")
data class Playlist(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    var name: String,
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "playlist_media",
        joinColumns = [JoinColumn(name = "playlist_id")],
        inverseJoinColumns = [JoinColumn(name = "media_id")]
    )
    var mediaItems: MutableList<Media> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    var creator: User? = null
)

