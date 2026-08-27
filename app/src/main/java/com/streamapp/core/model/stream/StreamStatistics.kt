package com.streamapp.core.model.stream

import kotlinx.serialization.Serializable

@Serializable
data class StreamStatistics(
    val currentFps: Float = 60f,
    val currentBitrateKbps: Int = 15_000,
    val droppedFramesTotal: Long = 0,
    val packetLossPercentage: Float = 0.0f,
    val jitterMs: Float = 1.2f,
    val roundTripTimeMs: Long = 18,
    val decodeTimeMs: Float = 2.4f,
    val currentResolution: Resolution = Resolution.R_1080P,
    val activeCodec: VideoCodec = VideoCodec.HEVC,
    val networkType: NetworkType = NetworkType.WIFI
)

@Serializable
enum class NetworkType {
    WIFI,
    ETHERNET,
    CELLULAR_5G,
    CELLULAR_4G,
    VPN,
    UNKNOWN
}
