package com.streamapp.core.broadcaster.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
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
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _availableDevices = MutableStateFlow<List<AudioInputDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioInputDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioInputDevice?>(null)
    val selectedDevice: StateFlow<AudioInputDevice?> = _selectedDevice.asStateFlow()

    // Live Mic Test State
    private val _isTestingMic = MutableStateFlow(false)
    val isTestingMic: StateFlow<Boolean> = _isTestingMic.asStateFlow()

    private val _micLevel = MutableStateFlow(0f) // 0.0f .. 1.0f
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _micDb = MutableStateFlow(-60)
    val micDb: StateFlow<Int> = _micDb.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var testJob: Job? = null

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        val deviceList = mutableListOf<AudioInputDevice>()

        // Always include default built-in microphone
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
                val typeName = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headset"
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
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
                            isBuiltIn = device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC,
                            isBluetooth = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            isUsb = device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET,
                            isWired = device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        )
                    )
                }
            }
        }

        _availableDevices.value = deviceList
        if (_selectedDevice.value == null) {
            _selectedDevice.value = deviceList.firstOrNull()
        }
    }

    fun selectDevice(device: AudioInputDevice) {
        _selectedDevice.value = device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device.id != -1) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val target = audioDevices.find { it.id == device.id }
            if (target != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.setCommunicationDevice(target)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startMicTest() {
        if (_isTestingMic.value) return

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _selectedDevice.value != null && _selectedDevice.value!!.id != -1) {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val target = audioDevices.find { it.id == _selectedDevice.value!!.id }
                if (target != null) {
                    audioRecord?.preferredDevice = target
                }
            }

            audioRecord?.startRecording()
            _isTestingMic.value = true

            testJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                while (isActive && _isTestingMic.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        // Amplitude normalized (0.0f .. 1.0f)
                        val normalized = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
                        // dB calculation (-60dB .. 0dB)
                        val db = if (rms > 1.0) (20 * log10(rms / 32767.0)).toInt().coerceIn(-60, 0) else -60

                        _micLevel.value = normalized
                        _micDb.value = db
                    }
                    delay(35) // ~30 fps updates
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopMicTest()
        }
    }

    fun stopMicTest() {
        _isTestingMic.value = false
        testJob?.cancel()
        testJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _micLevel.value = 0f
        _micDb.value = -60
    }
}
