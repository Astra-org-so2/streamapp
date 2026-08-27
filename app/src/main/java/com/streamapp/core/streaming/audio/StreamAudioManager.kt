package com.streamapp.core.streaming.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // User-configured volume and mute state
    private var userVolume = 1.0f
    private var isUserMuted = false

    // System-level focus transient states
    private var focusDuckingMultiplier = 1.0f
    private var isSystemFocusMuted = false

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AppLogger.i(LogCategory.AUDIO, "Headphones disconnected - auto muting stream audio")
                setUserMuted(true)
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLogger.w(LogCategory.AUDIO, "Audio focus lost permanently")
                isSystemFocusMuted = true
                focusDuckingMultiplier = 0.0f
                recomputeEffectiveAudioState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLogger.w(LogCategory.AUDIO, "Audio focus lost transiently")
                isSystemFocusMuted = false
                focusDuckingMultiplier = 0.2f
                recomputeEffectiveAudioState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                AppLogger.d(LogCategory.AUDIO, "Ducking audio volume")
                isSystemFocusMuted = false
                focusDuckingMultiplier = 0.3f
                recomputeEffectiveAudioState()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                AppLogger.i(LogCategory.AUDIO, "Audio focus gained -> restoring user volume and mute state")
                isSystemFocusMuted = false
                focusDuckingMultiplier = 1.0f
                recomputeEffectiveAudioState()
            }
        }
    }

    private fun recomputeEffectiveAudioState() {
        _isMuted.value = isUserMuted || isSystemFocusMuted
        _volume.value = (userVolume * focusDuckingMultiplier).coerceIn(0f, 1f)
    }

    fun requestAudioFocus(): Boolean {
        registerNoisyReceiver()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        unregisterNoisyReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun setUserMuted(muted: Boolean) {
        isUserMuted = muted
        recomputeEffectiveAudioState()
        AppLogger.d(LogCategory.AUDIO, "User stream audio muted=$muted")
    }

    fun toggleMute() {
        setUserMuted(!isUserMuted)
    }

    fun setUserVolume(vol: Float) {
        userVolume = vol.coerceIn(0f, 1f)
        recomputeEffectiveAudioState()
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            context.registerReceiver(
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
            isReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.AUDIO, "Error unregistering noisy receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }
}
