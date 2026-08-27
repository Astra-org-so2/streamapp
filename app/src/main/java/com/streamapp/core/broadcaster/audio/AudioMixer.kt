package com.streamapp.core.broadcaster.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AudioMixer @Inject constructor(
    private val voiceCleaner: VoiceCleaner
) {

    private var audioRecord: AudioRecord? = null
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun startRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )

            audioRecord?.let {
                voiceCleaner.applyAudioEffects(it.audioSessionId)
                it.startRecording()
                _isRecording.value = true
            }
        } catch (e: SecurityException) {
            // Handle missing RECORD_AUDIO permission
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        voiceCleaner.releaseEffects()
        _isRecording.value = false
    }
}
