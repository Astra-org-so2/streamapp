package com.streamapp.core.model

data class AppSettings(
    val videoResolution: String = "1080p",
    val videoBitrate: Int = 6000,
    val videoFps: Int = 60,
    val audioBitrate: Int = 160,
    val touchHeatmapEnabled: Boolean = false,
    val autoClipEnabled: Boolean = false,
    val chatOverlayEnabled: Boolean = true,
    val adaptiveBitrateEnabled: Boolean = true,
    val noiseSuppressionEnabled: Boolean = true,
    val isEchoCancellationEnabled: Boolean = true,
    val noiseGateThresholdDb: Int = -40,
    val noiseSuppressionLevel: String = "Студийный",
    val backgroundBlurEnabled: Boolean = false,
    val greenScreenEnabled: Boolean = false,
    val localRecordingEnabled: Boolean = false,
    val autoReconnectEnabled: Boolean = true,
    val lowLatencyModeEnabled: Boolean = true,
    val heatmapDecayTimeMs: Long = 1000L,
    val autoClipDurationS: Int = 30,
    val privacyGuardBlurIntensity: Float = 5f,

    // Audio Mixer persistence
    val micVolume: Float = 1.0f,
    val gameVolume: Float = 1.0f,
    val musicVolume: Float = 0.7f,
    val alertsVolume: Float = 1.0f,
    val masterVolume: Float = 1.0f,
    val isMicMuted: Boolean = false,
    val isGameMuted: Boolean = false,
    val isMusicMuted: Boolean = false,
    val isAlertsMuted: Boolean = false,

    // Game Screen persistence
    val selectedAppPackage: String = "",
    val captureModeOrdinal: Int = 0,
    val selectedMicDeviceId: Int = -1
)
