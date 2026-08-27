package com.streamapp.core.broadcaster.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class AudioMixer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceCleaner: VoiceCleaner,
    private val dispatchers: AppDispatchers
) {
    companion object {
        const val SAMPLE_RATE = 48000 // Broadcast Standard 48 kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_FRAME = 960 // 20 ms @ 48 kHz (standard Opus / AAC frame)
    }

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val mixerScope = CoroutineScope(dispatchers.io)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Live Mic VU Telemetry during recording
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _micDb = MutableStateFlow(-60)
    val micDb: StateFlow<Int> = _micDb.asStateFlow()

    // Real-time Mixed PCM Output Flow (16-bit PCM Little Endian)
    private val _mixedPcmFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val mixedPcmFlow: SharedFlow<ByteArray> = _mixedPcmFlow.asSharedFlow()

    // Callback for direct hardware encoder binding
    var onMixedPcmListener: ((ByteArray, Int) -> Unit)? = null

    // Channel Volume multipliers (0.0 to 1.5)
    var micVolume = 1.0f
    var gameVolume = 1.0f
    var musicVolume = 0.8f
    var masterVolume = 1.0f

    // Mute toggles
    var isMicMuted = false
    var isGameMuted = false
    var isMusicMuted = false

    // Hardware routing
    var preferredDevice: android.media.AudioDeviceInfo? = null

    // DSP Ducking
    var isAudioDuckingEnabled = true
    private val duckingFactor = 0.3f // Lower game/music to 30% when speaking
    private val duckingThreshold = 800f // RMS threshold for voice detection

    // Incoming queues for auxiliary channels
    private val gameAudioQueue = ConcurrentLinkedQueue<ShortArray>()
    private val musicAudioQueue = ConcurrentLinkedQueue<ShortArray>()

    private val recordLock = Any()

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun startRecording(): Boolean {
        if (_isRecording.value) {
            AppLogger.w(LogCategory.AUDIO, "AudioMixer is already recording")
            return true
        }

        if (!hasRecordAudioPermission()) {
            AppLogger.e(LogCategory.AUDIO, "RECORD_AUDIO permission not granted")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            AppLogger.e(LogCategory.AUDIO, "AudioRecord minBufferSize error: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(SAMPLES_PER_FRAME * 4)

        return synchronized(recordLock) {
            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    AppLogger.e(LogCategory.AUDIO, "AudioRecord initialization failed")
                    record.release()
                    return false
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    preferredDevice?.let { target ->
                        record.preferredDevice = target
                    }
                }

                voiceCleaner.applyAudioEffects(record.audioSessionId)
                record.startRecording()
                audioRecord = record
                _isRecording.value = true

                startPcmProcessingLoop(record)
                AppLogger.i(LogCategory.AUDIO, "AudioMixer recording started successfully at $SAMPLE_RATE Hz")
                true
            } catch (e: Exception) {
                AppLogger.e(LogCategory.AUDIO, "Failed to start AudioRecord", e)
                stopRecording()
                false
            }
        }
    }

    private fun startPcmProcessingLoop(record: AudioRecord) {
        recordJob?.cancel()
        recordJob = mixerScope.launch(dispatchers.io) {
            val micShortBuffer = ShortArray(SAMPLES_PER_FRAME)
            val mixedShortBuffer = ShortArray(SAMPLES_PER_FRAME)
            val outputByteBuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)

            while (isActive && _isRecording.value) {
                val readSamples = record.read(micShortBuffer, 0, SAMPLES_PER_FRAME)
                if (readSamples <= 0) continue

                // 1. Calculate Mic RMS for Voice Activity / Ducking / VU Telemetry
                var sumSquare = 0.0
                for (i in 0 until readSamples) {
                    sumSquare += (micShortBuffer[i] * micShortBuffer[i]).toDouble()
                }
                val micRms = sqrt(sumSquare / readSamples).toFloat()
                val isSpeaking = isAudioDuckingEnabled && !isMicMuted && (micRms > duckingThreshold)
                val currentDucking = if (isSpeaking) duckingFactor else 1.0f

                val normalized = (micRms / 32767.0).toFloat().coerceIn(0f, 1f)
                val db = if (micRms > 1.0) (20 * kotlin.math.log10(micRms / 32767.0)).toInt().coerceIn(-60, 0) else -60
                _micLevel.value = normalized
                _micDb.value = db

                // 2. Poll auxiliary channels
                val gamePcm = gameAudioQueue.poll()
                val musicPcm = musicAudioQueue.poll()

                // 3. Multi-Channel PCM Linear Mixing with Clamping
                val effectiveMicVol = if (isMicMuted) 0f else micVolume
                val effectiveGameVol = if (isGameMuted) 0f else (gameVolume * currentDucking)
                val effectiveMusicVol = if (isMusicMuted) 0f else (musicVolume * currentDucking)

                outputByteBuffer.clear()

                for (i in 0 until readSamples) {
                    val micSample = micShortBuffer[i] * effectiveMicVol
                    val gameSample = if (gamePcm != null && i < gamePcm.size) gamePcm[i] * effectiveGameVol else 0f
                    val musicSample = if (musicPcm != null && i < musicPcm.size) musicPcm[i] * effectiveMusicVol else 0f

                    val mixed = (micSample + gameSample + musicSample) * masterVolume
                    val clamped = mixed.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    mixedShortBuffer[i] = clamped
                    outputByteBuffer.putShort(clamped)
                }

                // 4. Dispatch mixed PCM frame to encoder listeners
                val byteCount = readSamples * 2
                val byteArray = ByteArray(byteCount)
                outputByteBuffer.flip()
                outputByteBuffer.get(byteArray, 0, byteCount)

                _mixedPcmFlow.emit(byteArray)
                onMixedPcmListener?.invoke(byteArray, byteCount)
            }
        }
    }

    fun feedGameAudio(pcmData: ShortArray) {
        if (_isRecording.value && gameAudioQueue.size < 10) {
            gameAudioQueue.offer(pcmData)
        }
    }

    fun feedMusicAudio(pcmData: ShortArray) {
        if (_isRecording.value && musicAudioQueue.size < 10) {
            musicAudioQueue.offer(pcmData)
        }
    }

    @Synchronized
    fun stopRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        synchronized(recordLock) {
            try {
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    record.release()
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.AUDIO, "Error stopping AudioRecord: ${e.message}")
            } finally {
                audioRecord = null
                voiceCleaner.releaseEffects()
                gameAudioQueue.clear()
                musicAudioQueue.clear()
            }
        }
        AppLogger.i(LogCategory.AUDIO, "AudioMixer stopped")
    }
}
