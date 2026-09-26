package com.pixl.backend.repository

import com.pixl.backend.model.Campaign
import org.springframework.data.jpa.repository.JpaRepository

interface CampaignRepository : JpaRepository<Campaign, Long>

