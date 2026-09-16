package com.viewo.backend.repository

import com.viewo.backend.model.ProofOfPlayLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProofOfPlayRepository : JpaRepository<ProofOfPlayLog, Long> {
    fun findByScreenId(screenId: Long): List<ProofOfPlayLog>
    fun findByScreenAssignedAdminId(adminId: Long): List<ProofOfPlayLog>
    
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    fun deleteByScreenId(screenId: Long)
    
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    fun deleteByMediaId(mediaId: Long)
}
