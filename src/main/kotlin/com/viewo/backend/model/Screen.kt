package com.viewo.backend.model

import jakarta.persistence.*

@Entity
@Table(name = "screens")
data class Screen(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    var name: String,
    
    var location: String,
    
    @Column(unique = true, nullable = false)
    var pairingCode: String,
    
    var status: String = "OFFLINE", // ONLINE or OFFLINE
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    var assignedAdmin: User? = null
)
