package com.pixl.backend.model

import jakarta.persistence.*

@Entity
@Table(name = "media")
data class Media(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    val filename: String,
    
    @Column(nullable = false, length = 1024)
    val publicUrl: String,
    
    val type: String, // IMAGE or VIDEO
    
    val durationSeconds: Int = 10,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    var uploader: User? = null
)

