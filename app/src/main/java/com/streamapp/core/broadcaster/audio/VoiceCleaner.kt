package com.streamapp.core.broadcaster.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCleaner @Inject constructor() {

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private val effectLock = Any()

    fun applyAudioEffects(audioSessionId: Int) {
        synchronized(effectLock) {
            // Always release previous native audio effects before creating new instances
            releaseEffects()

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = true
                    }
                    AppLogger.i(LogCategory.AUDIO, "NoiseSuppressor successfully created and enabled for session $audioSessionId")
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.AUDIO, "Failed to create NoiseSuppressor", e)
                }
            }

            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = true
                    }
                    AppLogger.i(LogCategory.AUDIO, "AcousticEchoCanceler successfully created and enabled for session $audioSessionId")
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.AUDIO, "Failed to create AcousticEchoCanceler", e)
                }
            }
        }
    }

    fun releaseEffects() {
        synchronized(effectLock) {
            try {
                noiseSuppressor?.let {
                    it.enabled = false
                    it.release()
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.AUDIO, "Error releasing NoiseSuppressor: ${e.message}")
            } finally {
                noiseSuppressor = null
            }

            try {
                echoCanceler?.let {
                    it.enabled = false
                    it.release()
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.AUDIO, "Error releasing AcousticEchoCanceler: ${e.message}")
            } finally {
                echoCanceler = null
            }
        }
    }
}
