package com.streamapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streamapp.core.model.stream.Resolution
import com.streamapp.core.model.stream.TargetFps
import com.streamapp.core.model.stream.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stream_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val MAX_BITRATE = intPreferencesKey("max_bitrate_kbps")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val LOW_LATENCY = booleanPreferencesKey("low_latency")
        val VIRTUAL_GAMEPAD = booleanPreferencesKey("virtual_gamepad")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    }

    val resolutionFlow: Flow<Resolution> = context.dataStore.data.map { prefs ->
        val name = prefs[PreferencesKeys.RESOLUTION] ?: Resolution.R_1080P.name
        try { Resolution.valueOf(name) } catch (e: Exception) { Resolution.R_1080P }
    }

    val targetFpsFlow: Flow<TargetFps> = context.dataStore.data.map { prefs ->
        val fps = prefs[PreferencesKeys.TARGET_FPS] ?: 60
        when (fps) {
            30 -> TargetFps.FPS_30
            120 -> TargetFps.FPS_120
            else -> TargetFps.FPS_60
        }
    }

    val maxBitrateFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.MAX_BITRATE] ?: 20_000
    }

    val videoCodecFlow: Flow<VideoCodec> = context.dataStore.data.map { prefs ->
        val name = prefs[PreferencesKeys.VIDEO_CODEC] ?: VideoCodec.HEVC.name
        try { VideoCodec.valueOf(name) } catch (e: Exception) { VideoCodec.HEVC }
    }

    val hwDecodingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HW_DECODING] ?: true
    }

    val lowLatencyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LOW_LATENCY] ?: true
    }

    val virtualGamepadFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VIRTUAL_GAMEPAD] ?: true
    }

    val hapticFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HAPTIC_FEEDBACK] ?: true
    }

    suspend fun setResolution(resolution: Resolution) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.RESOLUTION] = resolution.name }
    }

    suspend fun setTargetFps(fps: TargetFps) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.TARGET_FPS] = fps.value }
    }

    suspend fun setMaxBitrate(bitrateKbps: Int) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.MAX_BITRATE] = bitrateKbps }
    }

    suspend fun setVideoCodec(codec: VideoCodec) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.VIDEO_CODEC] = codec.name }
    }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.HW_DECODING] = enabled }
    }

    suspend fun setLowLatency(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.LOW_LATENCY] = enabled }
    }

    suspend fun setVirtualGamepad(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.VIRTUAL_GAMEPAD] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.HAPTIC_FEEDBACK] = enabled }
    }
}
