package com.streamapp.core.model.stream

import kotlinx.serialization.Serializable

@Serializable
data class StreamConfiguration(
    val serverId: String,
    val appId: String,
    val targetResolution: Resolution = Resolution.R_1080P,
    val targetFps: TargetFps = TargetFps.FPS_60,
    val maxBitrateKbps: Int = 20_000,
    val minBitrateKbps: Int = 4_000,
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val audioBitrateKbps: Int = 160,
    val enableHardwareDecoding: Boolean = true,
    val lowLatencyMode: Boolean = true,
    val enableVirtualGamepad: Boolean = true,
    val virtualGamepadLayoutId: String = "default"
)

@Serializable
enum class Resolution(val width: Int, val height: Int, val label: String) {
    R_720P(1280, 720, "720p"),
    R_1080P(1920, 1080, "1080p"),
    R_1440P(2560, 1440, "1440p"),
    R_4K(3840, 2160, "4K")
}

@Serializable
enum class TargetFps(val value: Int) {
    FPS_30(30),
    FPS_60(60),
    FPS_120(120)
}

@Serializable
enum class VideoCodec(val mimeType: String, val displayName: String) {
    H264("video/avc", "H.264 (AVC)"),
    HEVC("video/hevc", "HEVC (H.265)"),
    VP9("video/x-vnd.on2.vp9", "VP9"),
    AV1("video/av01", "AV1")
}
