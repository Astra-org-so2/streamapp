package com.streamapp.core.model.server

import kotlinx.serialization.Serializable

@Serializable
data class StreamServer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 47990,
    val protocol: StreamProtocol = StreamProtocol.WEBRTC,
    val authType: AuthType = AuthType.NONE,
    val isDefault: Boolean = false,
    val status: ServerStatus = ServerStatus.ONLINE,
    val latencyMs: Long? = null,
    val serverVersion: String? = null,
    val availableStreamsCount: Int = 0
)

@Serializable
enum class StreamProtocol {
    WEBRTC,
    MOONLIGHT,
    CUSTOM_UDP
}

@Serializable
enum class AuthType {
    NONE,
    PIN,
    PASSWORD,
    TOKEN
}

@Serializable
enum class ServerStatus {
    ONLINE,
    OFFLINE,
    BUSY,
    UNREACHABLE
}

enum class LatencyQuality {
    EXCELLENT, // < 20ms
    GOOD,      // 20 - 45ms
    AVERAGE,   // 45 - 80ms
    POOR,      // > 80ms
    OFFLINE;

    companion object {
        fun fromMs(latencyMs: Long?): LatencyQuality = when {
            latencyMs == null -> OFFLINE
            latencyMs < 20 -> EXCELLENT
            latencyMs < 45 -> GOOD
            latencyMs < 80 -> AVERAGE
            else -> POOR
        }
    }
}
