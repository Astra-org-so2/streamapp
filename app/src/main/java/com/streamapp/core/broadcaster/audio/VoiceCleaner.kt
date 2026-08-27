package com.streamapp.core.broadcaster.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCleaner @Inject constructor() {

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    fun applyAudioEffects(audioSessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true
                Log.d("VoiceCleaner", "NoiseSuppressor enabled")
            } catch (e: Exception) {
                Log.e("VoiceCleaner", "Failed to create NoiseSuppressor", e)
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                echoCanceler?.enabled = true
                Log.d("VoiceCleaner", "AcousticEchoCanceler enabled")
            } catch (e: Exception) {
                Log.e("VoiceCleaner", "Failed to create AcousticEchoCanceler", e)
            }
        }
    }

    fun releaseEffects() {
        noiseSuppressor?.release()
        noiseSuppressor = null

        echoCanceler?.release()
        echoCanceler = null
    }
}
