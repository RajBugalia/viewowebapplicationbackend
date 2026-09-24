package com.viewo.backend.dto

data class CreatePlaylistRequest(
    val name: String,
    val mediaIds: List<Long>
)

data class ZoneConfigDto(
    val zoneIndex: Int,
    val row: Int,
    val col: Int,
    val playlistId: Long
)

data class CreateCampaignRequest(
    val name: String,
    val startDate: String, // ISO datetime string
    val endDate: String,
    val playlistId: Long? = null,
    val targetScreenIds: List<Long>,
    val layoutType: String? = "SINGLE",
    val splitRows: Int? = 1,
    val splitCols: Int? = 1,
    val zones: List<ZoneConfigDto>? = null
)
