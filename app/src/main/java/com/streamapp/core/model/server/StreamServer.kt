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
    CUSTOM_UDP,
    UNKNOWN;

    companion object {
        fun fromString(value: String): StreamProtocol =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
enum class AuthType {
    NONE,
    PIN,
    PASSWORD,
    TOKEN,
    UNKNOWN;

    companion object {
        fun fromString(value: String): AuthType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
enum class ServerStatus {
    ONLINE,
    OFFLINE,
    BUSY,
    UNREACHABLE,
    UNKNOWN;

    companion object {
        fun fromString(value: String): ServerStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
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
