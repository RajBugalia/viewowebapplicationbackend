package com.viewo.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "proof_of_play_logs")
data class ProofOfPlayLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id")
    val screen: Screen,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    val media: Media,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    val campaign: Campaign? = null,
    
    val duration: Int? = null,
    
    val status: String? = null,
    
    val playedAt: LocalDateTime
)
