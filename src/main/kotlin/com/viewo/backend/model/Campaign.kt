package com.viewo.backend.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "campaigns")
data class Campaign(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    var name: String,
    
    val startDate: Instant,
    
    val endDate: Instant,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id")
    var playlist: Playlist? = null,
    
    var layoutType: String = "SINGLE", // SINGLE or SPLIT
    var splitRows: Int = 1,
    var splitCols: Int = 1,
    
    @Column(columnDefinition = "TEXT")
    var zoneConfigJson: String? = null,
    
    @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "campaign_screens",
        joinColumns = [JoinColumn(name = "campaign_id")],
        inverseJoinColumns = [JoinColumn(name = "screen_id")]
    )
    var targetScreens: MutableList<Screen> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    var creator: User? = null
)
