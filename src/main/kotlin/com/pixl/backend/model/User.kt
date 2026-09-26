package com.pixl.backend.model

import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    var name: String? = "",
    
    @Column(unique = true, nullable = false)
    val email: String,
    
    @Column(nullable = false)
    var passwordHash: String,
    
    @Enumerated(EnumType.STRING)
    val role: Role,
    
    var theme: String? = "light",
    var timezone: String? = "UTC",
    var notifyAlerts: Boolean? = true,
    var notifyReports: Boolean? = true,
    var notifyUpdates: Boolean? = false
)

