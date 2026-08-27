package com.streamapp.core.features.autoclip

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class AutoClipDetector @Inject constructor() {

    private val _hypeEvents = MutableSharedFlow<HypeEvent>()
    val hypeEvents: SharedFlow<HypeEvent> = _hypeEvents.asSharedFlow()

    private var recentMessageCount = 0
    private var lastMessageResetTime = System.currentTimeMillis()

    suspend fun onAudioAmplitude(amplitude: Float) {
        if (amplitude > 0.85f) {
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
            triggerHypeEvent(HypeEvent.ChatSpam)
            recentMessageCount = 0
        }
    }

    private suspend fun triggerHypeEvent(event: HypeEvent) {
        _hypeEvents.emit(event)
    }
}

sealed class HypeEvent {
    data class LoudAudio(val amplitude: Float) : HypeEvent()
    object ChatSpam : HypeEvent()
}
