package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamapp.core.model.BroadcastDestination
import com.streamapp.core.model.DestinationConnectionState
import com.streamapp.core.model.DestinationValidator
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
) {
    fun toDomain(connectionState: DestinationConnectionState = DestinationConnectionState.DISABLED): BroadcastDestination =
        BroadcastDestination(
            id = id,
            platform = platform,
            name = name,
            rtmpUrl = rtmpUrl,
            streamKey = streamKey,
            isEnabled = isEnabled,
            connectionState = connectionState
        )

    fun sanitized(): DestinationEntity = copy(
        name = name.trim(),
        rtmpUrl = DestinationValidator.validateAndNormalizeRtmpUrl(rtmpUrl) ?: rtmpUrl.trim(),
        streamKey = DestinationValidator.sanitizeStreamKey(streamKey) ?: streamKey.trim()
    )
}
