package com.streamapp.core.broadcaster.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioInputDevice(
    val id: Int,
    val name: String,
    val typeName: String,
    val isBuiltIn: Boolean,
    val isBluetooth: Boolean,
    val isUsb: Boolean,
    val isWired: Boolean
)

@Singleton
class MicrophoneController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers
) {
    companion object {
        const val SAMPLE_RATE = 48000 // Standard 48 kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(dispatchers.default + SupervisorJob())

    private val _availableDevices = MutableStateFlow<List<AudioInputDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioInputDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioInputDevice?>(null)
    val selectedDevice: StateFlow<AudioInputDevice?> = _selectedDevice.asStateFlow()

    // Live Mic Test State
    private val _isTestingMic = MutableStateFlow(false)
    val isTestingMic: StateFlow<Boolean> = _isTestingMic.asStateFlow()

    private val _micLevel = MutableStateFlow(0f) // 0.0f .. 1.0f
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _micDb = MutableStateFlow(-60) // -60 dB .. 0 dB
    val micDb: StateFlow<Int> = _micDb.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var testJob: Job? = null
    private val testLock = Any()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            AppLogger.i(LogCategory.AUDIO, "Audio devices connected -> refreshing mic list")
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            AppLogger.i(LogCategory.AUDIO, "Audio devices disconnected -> refreshing mic list")
            refreshDevices()
        }
    }

    init {
        refreshDevices()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshDevices() {
        val deviceList = mutableListOf<AudioInputDevice>()

        // Default built-in microphone
        deviceList.add(
            AudioInputDevice(
                id = -1,
                name = "Встроенный микрофон (По умолчанию)",
                typeName = "Built-in",
                isBuiltIn = true,
                isBluetooth = false,
                isUsb = false,
                isWired = false
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.forEach { device ->
                // Note: TYPE_BLUETOOTH_A2DP is an OUTPUT-only profile. For input we check SCO and BLE headset.
                val isBt = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                val isUsb = device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                val isWired = device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET

                val typeName = when {
                    isBt -> "Bluetooth"
                    isUsb -> "USB Audio"
                    isWired -> "Wired Headset"
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                    else -> "Audio Input"
                }

                val displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !device.productName.isNullOrBlank()) {
                    device.productName.toString()
                } else {
                    "$typeName (${device.id})"
                }

                if (device.type != AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    deviceList.add(
                        AudioInputDevice(
                            id = device.id,
                            name = displayName,
                            typeName = typeName,
                            isBuiltIn = false,
                            isBluetooth = isBt,
                            isUsb = isUsb,
                            isWired = isWired
                        )
                    )
                }
            }
        }

        _availableDevices.value = deviceList
        if (_selectedDevice.value == null || deviceList.none { it.id == _selectedDevice.value?.id }) {
            _selectedDevice.value = deviceList.firstOrNull()
        }
    }

    fun selectDevice(device: AudioInputDevice) {
        _selectedDevice.value = device
        AppLogger.i(LogCategory.AUDIO, "Selected microphone device: ${device.name} (id: ${device.id})")
    }

    fun getPreferredAudioDeviceInfo(): AudioDeviceInfo? {
        val selected = _selectedDevice.value ?: return null
        if (selected.id == -1 || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return audioDevices.find { it.id == selected.id }
    }

    fun startMicTest(): Boolean {
        if (_isTestingMic.value) return true

        if (!hasRecordAudioPermission()) {
            AppLogger.e(LogCategory.AUDIO, "Cannot start mic test: RECORD_AUDIO permission missing")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            AppLogger.e(LogCategory.AUDIO, "AudioRecord minBufferSize error: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        return synchronized(testLock) {
            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    AppLogger.e(LogCategory.AUDIO, "Failed to initialize test AudioRecord")
                    record.release()
                    return false
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    getPreferredAudioDeviceInfo()?.let { target ->
                        record.preferredDevice = target
                    }
                }

                record.startRecording()
                audioRecord = record
                _isTestingMic.value = true

                testJob?.cancel()
                testJob = scope.launch(dispatchers.io) {
                    val buffer = ShortArray(bufferSize / 2)
                    while (isActive && _isTestingMic.value) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += (buffer[i] * buffer[i]).toDouble()
                            }
                            val rms = sqrt(sum / read)
                            // Normalized amplitude 0.0f .. 1.0f
                            val normalized = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
                            // dB scale -60dB .. 0dB
                            val db = if (rms > 1.0) (20 * log10(rms / 32767.0)).toInt().coerceIn(-60, 0) else -60

                            _micLevel.value = normalized
                            _micDb.value = db
                        }
                        delay(35) // ~30 fps updates
                    }
                }
                AppLogger.i(LogCategory.AUDIO, "Microphone test started successfully")
                true
            } catch (e: Exception) {
                AppLogger.e(LogCategory.AUDIO, "Error during startMicTest", e)
                stopMicTest()
                false
            }
        }
    }

    fun stopMicTest() {
        synchronized(testLock) {
            _isTestingMic.value = false
            testJob?.cancel()
            testJob = null
            try {
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    record.release()
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.AUDIO, "Error stopping test AudioRecord: ${e.message}")
            } finally {
                audioRecord = null
                _micLevel.value = 0f
                _micDb.value = -60
            }
        }
        AppLogger.i(LogCategory.AUDIO, "Microphone test stopped")
    }

    fun release() {
        stopMicTest()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
    }
}
