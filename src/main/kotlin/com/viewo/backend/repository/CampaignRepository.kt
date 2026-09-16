package com.viewo.backend.repository

import com.viewo.backend.model.Campaign
import org.springframework.data.jpa.repository.JpaRepository

interface CampaignRepository : JpaRepository<Campaign, Long>
