package com.viewo.backend.dto

data class CreatePlaylistRequest(
    val name: String,
    val mediaIds: List<Long>
)

data class CreateCampaignRequest(
    val name: String,
    val startDate: String, // ISO datetime string e.g. "2026-09-16T14:30:00"
    val endDate: String,
    val playlistId: Long,
    val targetScreenIds: List<Long>
)
