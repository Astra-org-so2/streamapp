package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_tracks")
data class AudioTrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String = "Local Audio",
    val filePath: String,
    val durationMs: Long = 0L,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
