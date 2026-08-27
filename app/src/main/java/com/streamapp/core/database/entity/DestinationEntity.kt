package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamapp.core.model.Platform

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey
    val id: String,
    val platform: Platform,
    val name: String,
    val rtmpUrl: String,
    val streamKey: String,
    val isEnabled: Boolean
)
