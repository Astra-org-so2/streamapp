package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamapp.core.model.app.AppCategory
import com.streamapp.core.model.app.StreamAppInfo

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val bannerUrl: String,
    val category: String,
    val serverId: String,
    val serverName: String,
    val lastPlayedTimestamp: Long?,
    val isFavorite: Boolean,
    val estimatedLatencyMs: Long?
) {
    fun toDomain(): StreamAppInfo = StreamAppInfo(
        id = id,
        title = title,
        description = description,
        coverUrl = coverUrl,
        bannerUrl = bannerUrl,
        category = try { AppCategory.valueOf(category) } catch (e: Exception) { AppCategory.GAME },
        serverId = serverId,
        serverName = serverName,
        lastPlayedTimestamp = lastPlayedTimestamp,
        isFavorite = isFavorite,
        estimatedLatencyMs = estimatedLatencyMs
    )

    companion object {
        fun fromDomain(app: StreamAppInfo): GameEntity = GameEntity(
            id = app.id,
            title = app.title,
            description = app.description,
            coverUrl = app.coverUrl,
            bannerUrl = app.bannerUrl,
            category = app.category.name,
            serverId = app.serverId,
            serverName = app.serverName,
            lastPlayedTimestamp = app.lastPlayedTimestamp,
            isFavorite = app.isFavorite,
            estimatedLatencyMs = app.estimatedLatencyMs
        )
    }
}
