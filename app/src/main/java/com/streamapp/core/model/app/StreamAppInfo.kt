package com.streamapp.core.model.app

import kotlinx.serialization.Serializable

@Serializable
data class StreamAppInfo(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val bannerUrl: String,
    val category: AppCategory = AppCategory.GAME,
    val serverId: String,
    val serverName: String = "",
    val lastPlayedTimestamp: Long? = null,
    val isFavorite: Boolean = false,
    val estimatedLatencyMs: Long? = null
)

@Serializable
enum class AppCategory(val displayName: String) {
    GAME("Games"),
    APPLICATION("Apps"),
    DESKTOP("Desktop")
}
