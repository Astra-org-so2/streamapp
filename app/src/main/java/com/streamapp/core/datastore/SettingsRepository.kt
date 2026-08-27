package com.streamapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.streamapp.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val writeMutex = Mutex()

    private object Keys {
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        
        val TOUCH_HEATMAP = booleanPreferencesKey("touch_heatmap")
        val AUTO_CLIP = booleanPreferencesKey("auto_clip")
        val CHAT_OVERLAY = booleanPreferencesKey("chat_overlay")
        val ADAPTIVE_BITRATE = booleanPreferencesKey("adaptive_bitrate")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val NOISE_GATE_THRESHOLD = intPreferencesKey("noise_gate_threshold")
        val NOISE_SUPPRESSION_LEVEL = stringPreferencesKey("noise_suppression_level")
        val BACKGROUND_BLUR = booleanPreferencesKey("background_blur")
        val GREEN_SCREEN = booleanPreferencesKey("green_screen")
        val LOCAL_RECORDING = booleanPreferencesKey("local_recording")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val LOW_LATENCY = booleanPreferencesKey("low_latency")
        val HEATMAP_DECAY = longPreferencesKey("heatmap_decay")
        val AUTO_CLIP_DURATION = intPreferencesKey("auto_clip_duration")
        val PRIVACY_GUARD_BLUR = floatPreferencesKey("privacy_guard_blur")

        // Mixer volumes
        val MIC_VOLUME = floatPreferencesKey("mic_volume")
        val GAME_VOLUME = floatPreferencesKey("game_volume")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val ALERTS_VOLUME = floatPreferencesKey("alerts_volume")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val IS_MIC_MUTED = booleanPreferencesKey("is_mic_muted")
        val IS_GAME_MUTED = booleanPreferencesKey("is_game_muted")
        val IS_MUSIC_MUTED = booleanPreferencesKey("is_music_muted")
        val IS_ALERTS_MUTED = booleanPreferencesKey("is_alerts_muted")

        // Game stream & mic device
        val SELECTED_APP_PACKAGE = stringPreferencesKey("selected_app_package")
        val CAPTURE_MODE_ORDINAL = intPreferencesKey("capture_mode_ordinal")
        val SELECTED_MIC_DEVICE_ID = intPreferencesKey("selected_mic_device_id")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            videoResolution = prefs[Keys.VIDEO_RESOLUTION] ?: "1080p",
            videoBitrate = prefs[Keys.VIDEO_BITRATE] ?: 6000,
            videoFps = prefs[Keys.VIDEO_FPS] ?: 60,
            audioBitrate = prefs[Keys.AUDIO_BITRATE] ?: 160,
            touchHeatmapEnabled = prefs[Keys.TOUCH_HEATMAP] ?: false,
            autoClipEnabled = prefs[Keys.AUTO_CLIP] ?: false,
            chatOverlayEnabled = prefs[Keys.CHAT_OVERLAY] ?: true,
            adaptiveBitrateEnabled = prefs[Keys.ADAPTIVE_BITRATE] ?: true,
            noiseSuppressionEnabled = prefs[Keys.NOISE_SUPPRESSION] ?: true,
            isEchoCancellationEnabled = prefs[Keys.ECHO_CANCELLATION] ?: true,
            noiseGateThresholdDb = prefs[Keys.NOISE_GATE_THRESHOLD] ?: -40,
            noiseSuppressionLevel = prefs[Keys.NOISE_SUPPRESSION_LEVEL] ?: "Студийный",
            backgroundBlurEnabled = prefs[Keys.BACKGROUND_BLUR] ?: false,
            greenScreenEnabled = prefs[Keys.GREEN_SCREEN] ?: false,
            localRecordingEnabled = prefs[Keys.LOCAL_RECORDING] ?: false,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT] ?: true,
            lowLatencyModeEnabled = prefs[Keys.LOW_LATENCY] ?: true,
            heatmapDecayTimeMs = prefs[Keys.HEATMAP_DECAY] ?: 1000L,
            autoClipDurationS = prefs[Keys.AUTO_CLIP_DURATION] ?: 30,
            privacyGuardBlurIntensity = prefs[Keys.PRIVACY_GUARD_BLUR] ?: 5f,
            micVolume = prefs[Keys.MIC_VOLUME] ?: 1.0f,
            gameVolume = prefs[Keys.GAME_VOLUME] ?: 1.0f,
            musicVolume = prefs[Keys.MUSIC_VOLUME] ?: 0.7f,
            alertsVolume = prefs[Keys.ALERTS_VOLUME] ?: 1.0f,
            masterVolume = prefs[Keys.MASTER_VOLUME] ?: 1.0f,
            isMicMuted = prefs[Keys.IS_MIC_MUTED] ?: false,
            isGameMuted = prefs[Keys.IS_GAME_MUTED] ?: false,
            isMusicMuted = prefs[Keys.IS_MUSIC_MUTED] ?: false,
            isAlertsMuted = prefs[Keys.IS_ALERTS_MUTED] ?: false,
            selectedAppPackage = prefs[Keys.SELECTED_APP_PACKAGE] ?: "",
            captureModeOrdinal = prefs[Keys.CAPTURE_MODE_ORDINAL] ?: 0,
            selectedMicDeviceId = prefs[Keys.SELECTED_MIC_DEVICE_ID] ?: -1
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                prefs[Keys.VIDEO_RESOLUTION] = settings.videoResolution
                prefs[Keys.VIDEO_BITRATE] = settings.videoBitrate
                prefs[Keys.VIDEO_FPS] = settings.videoFps
                prefs[Keys.AUDIO_BITRATE] = settings.audioBitrate
                prefs[Keys.TOUCH_HEATMAP] = settings.touchHeatmapEnabled
                prefs[Keys.AUTO_CLIP] = settings.autoClipEnabled
                prefs[Keys.CHAT_OVERLAY] = settings.chatOverlayEnabled
                prefs[Keys.ADAPTIVE_BITRATE] = settings.adaptiveBitrateEnabled
                prefs[Keys.NOISE_SUPPRESSION] = settings.noiseSuppressionEnabled
                prefs[Keys.ECHO_CANCELLATION] = settings.isEchoCancellationEnabled
                prefs[Keys.NOISE_GATE_THRESHOLD] = settings.noiseGateThresholdDb
                prefs[Keys.NOISE_SUPPRESSION_LEVEL] = settings.noiseSuppressionLevel
                prefs[Keys.BACKGROUND_BLUR] = settings.backgroundBlurEnabled
                prefs[Keys.GREEN_SCREEN] = settings.greenScreenEnabled
                prefs[Keys.LOCAL_RECORDING] = settings.localRecordingEnabled
                prefs[Keys.AUTO_RECONNECT] = settings.autoReconnectEnabled
                prefs[Keys.LOW_LATENCY] = settings.lowLatencyModeEnabled
                prefs[Keys.HEATMAP_DECAY] = settings.heatmapDecayTimeMs
                prefs[Keys.AUTO_CLIP_DURATION] = settings.autoClipDurationS
                prefs[Keys.PRIVACY_GUARD_BLUR] = settings.privacyGuardBlurIntensity
                prefs[Keys.MIC_VOLUME] = settings.micVolume
                prefs[Keys.GAME_VOLUME] = settings.gameVolume
                prefs[Keys.MUSIC_VOLUME] = settings.musicVolume
                prefs[Keys.ALERTS_VOLUME] = settings.alertsVolume
                prefs[Keys.MASTER_VOLUME] = settings.masterVolume
                prefs[Keys.IS_MIC_MUTED] = settings.isMicMuted
                prefs[Keys.IS_GAME_MUTED] = settings.isGameMuted
                prefs[Keys.IS_MUSIC_MUTED] = settings.isMusicMuted
                prefs[Keys.IS_ALERTS_MUTED] = settings.isAlertsMuted
                prefs[Keys.SELECTED_APP_PACKAGE] = settings.selectedAppPackage
                prefs[Keys.CAPTURE_MODE_ORDINAL] = settings.captureModeOrdinal
                prefs[Keys.SELECTED_MIC_DEVICE_ID] = settings.selectedMicDeviceId
            }
        }
    }

    suspend fun updateVolumes(
        mic: Float? = null,
        game: Float? = null,
        music: Float? = null,
        alerts: Float? = null,
        master: Float? = null,
        micMuted: Boolean? = null,
        gameMuted: Boolean? = null,
        musicMuted: Boolean? = null,
        alertsMuted: Boolean? = null
    ) {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                mic?.let { prefs[Keys.MIC_VOLUME] = it }
                game?.let { prefs[Keys.GAME_VOLUME] = it }
                music?.let { prefs[Keys.MUSIC_VOLUME] = it }
                alerts?.let { prefs[Keys.ALERTS_VOLUME] = it }
                master?.let { prefs[Keys.MASTER_VOLUME] = it }
                micMuted?.let { prefs[Keys.IS_MIC_MUTED] = it }
                gameMuted?.let { prefs[Keys.IS_GAME_MUTED] = it }
                musicMuted?.let { prefs[Keys.IS_MUSIC_MUTED] = it }
                alertsMuted?.let { prefs[Keys.IS_ALERTS_MUTED] = it }
            }
        }
    }
}
