package com.pixl.backend.security.services

import com.pixl.backend.model.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserDetailsImpl(
    val id: Long,
    val name: String,
    private val email: String,
    private val passwordHash: String,
    private val authorities: Collection<GrantedAuthority>
) : UserDetails {

    companion object {
        fun build(user: User): UserDetailsImpl {
            val authorities = listOf(SimpleGrantedAuthority(user.role.name))
            return UserDetailsImpl(
                user.id,
                user.name ?: "",
                user.email,
                user.passwordHash,
                authorities
            )
        }
    }

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
    override fun getPassword(): String = passwordHash
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}

