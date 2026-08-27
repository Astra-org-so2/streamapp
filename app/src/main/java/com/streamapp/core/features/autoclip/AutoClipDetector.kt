package com.streamapp.core.features.autoclip

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class HypeEvent {
    data class LoudAudio(val amplitude: Float) : HypeEvent()
    data object ChatSpam : HypeEvent()
}

@Singleton
class AutoClipDetector @Inject constructor() {

    private val _hypeEvents = MutableSharedFlow<HypeEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val hypeEvents: SharedFlow<HypeEvent> = _hypeEvents.asSharedFlow()

    private var recentMessageCount = 0
    private var lastMessageResetTime = System.currentTimeMillis()
    private var lastLoudAudioEventTime = 0L
    private val loudAudioCooldownMs = 4000L // 4 seconds cooldown to avoid flooding clip triggers

    suspend fun onAudioAmplitude(amplitude: Float) {
        val now = System.currentTimeMillis()
        if (amplitude > 0.85f && (now - lastLoudAudioEventTime) >= loudAudioCooldownMs) {
            lastLoudAudioEventTime = now
            AppLogger.i(LogCategory.FEATURES, "AutoClipDetector triggered LoudAudio event (amplitude: $amplitude)")
            triggerHypeEvent(HypeEvent.LoudAudio(amplitude))
        }
    }

    suspend fun onChatMessageReceived() {
        val now = System.currentTimeMillis()
        if (now - lastMessageResetTime > 10000) {
            recentMessageCount = 0
            lastMessageResetTime = now
        }
        recentMessageCount++

        if (recentMessageCount > 50) {
            AppLogger.i(LogCategory.FEATURES, "AutoClipDetector triggered ChatSpam event ($recentMessageCount msgs/10s)")
            triggerHypeEvent(HypeEvent.ChatSpam)
            recentMessageCount = 0
        }
    }

    private suspend fun triggerHypeEvent(event: HypeEvent) {
        _hypeEvents.emit(event)
    }
}
