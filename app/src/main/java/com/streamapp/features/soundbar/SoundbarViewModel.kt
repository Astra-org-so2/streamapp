package com.streamapp.features.soundbar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.broadcaster.audio.AudioInputDevice
import com.streamapp.core.broadcaster.audio.AudioManagerController
import com.streamapp.core.broadcaster.audio.AudioTrackItem
import com.streamapp.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundbarViewModel @Inject constructor(
    private val audioManagerController: AudioManagerController,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val micVolume: StateFlow<Float> = audioManagerController.micVolume
    val isMicMuted: StateFlow<Boolean> = audioManagerController.isMicMuted

    val gameVolume: StateFlow<Float> = audioManagerController.gameVolume
    val isGameMuted: StateFlow<Boolean> = audioManagerController.isGameMuted

    val musicVolume: StateFlow<Float> = audioManagerController.musicVolume
    val isMusicMuted: StateFlow<Boolean> = audioManagerController.isMusicMuted

    val alertsVolume: StateFlow<Float> = audioManagerController.alertsVolume
    val isAlertsMuted: StateFlow<Boolean> = audioManagerController.isAlertsMuted

    val masterVolume: StateFlow<Float> = audioManagerController.masterVolume

    val playlist: StateFlow<List<AudioTrackItem>> = audioManagerController.playlist
    val currentTrack: StateFlow<AudioTrackItem?> = audioManagerController.currentTrack
    val isPlaying: StateFlow<Boolean> = audioManagerController.isPlaying
    val isLooping: StateFlow<Boolean> = audioManagerController.isLooping
    val isAudioDuckingEnabled: StateFlow<Boolean> = audioManagerController.isAudioDuckingEnabled

    // Noise Suppression & DSP Voice Cleaner State
    private val _isNoiseSuppressionEnabled = MutableStateFlow(true)
    val isNoiseSuppressionEnabled: StateFlow<Boolean> = _isNoiseSuppressionEnabled.asStateFlow()

    private val _isEchoCancellationEnabled = MutableStateFlow(true)
    val isEchoCancellationEnabled: StateFlow<Boolean> = _isEchoCancellationEnabled.asStateFlow()

    private val _noiseGateThresholdDb = MutableStateFlow(-40)
    val noiseGateThresholdDb: StateFlow<Int> = _noiseGateThresholdDb.asStateFlow()

    private val _noiseSuppressionLevel = MutableStateFlow("Студийный")
    val noiseSuppressionLevel: StateFlow<String> = _noiseSuppressionLevel.asStateFlow()

    // Mic device & live VU meter test
    val micController = audioManagerController.microphoneController
    val availableMicDevices: StateFlow<List<AudioInputDevice>> = micController.availableDevices
    val selectedMicDevice: StateFlow<AudioInputDevice?> = micController.selectedDevice
    val isTestingMic: StateFlow<Boolean> = micController.isTestingMic
    val micLevel: StateFlow<Float> = micController.micLevel
    val micDb: StateFlow<Int> = micController.micDb

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.firstOrNull() ?: return@launch
            _isNoiseSuppressionEnabled.value = s.noiseSuppressionEnabled
            _isEchoCancellationEnabled.value = s.isEchoCancellationEnabled
            _noiseGateThresholdDb.value = s.noiseGateThresholdDb
            _noiseSuppressionLevel.value = s.noiseSuppressionLevel
        }
    }

    private fun persistDspSettings() {
        viewModelScope.launch {
            val current = settingsRepository.settings.firstOrNull() ?: return@launch
            settingsRepository.updateSettings(
                current.copy(
                    noiseSuppressionEnabled = _isNoiseSuppressionEnabled.value,
                    isEchoCancellationEnabled = _isEchoCancellationEnabled.value,
                    noiseGateThresholdDb = _noiseGateThresholdDb.value,
                    noiseSuppressionLevel = _noiseSuppressionLevel.value
                )
            )
        }
    }

    fun toggleNoiseSuppression() {
        _isNoiseSuppressionEnabled.value = !_isNoiseSuppressionEnabled.value
        persistDspSettings()
    }

    fun toggleEchoCancellation() {
        _isEchoCancellationEnabled.value = !_isEchoCancellationEnabled.value
        persistDspSettings()
    }

    fun setNoiseGateThreshold(threshold: Int) {
        _noiseGateThresholdDb.value = threshold
        persistDspSettings()
    }

    fun setNoiseSuppressionLevel(level: String) {
        _noiseSuppressionLevel.value = level
        persistDspSettings()
    }

    fun setMicVolume(volume: Float) = audioManagerController.setMicVolume(volume)
    fun toggleMicMute() = audioManagerController.toggleMicMute()

    fun setGameVolume(volume: Float) = audioManagerController.setGameVolume(volume)
    fun toggleGameMute() = audioManagerController.toggleGameMute()

    fun setMusicVolume(volume: Float) = audioManagerController.setMusicVolume(volume)
    fun toggleMusicMute() = audioManagerController.toggleMusicMute()

    fun setAlertsVolume(volume: Float) = audioManagerController.setAlertsVolume(volume)
    fun toggleAlertsMute() = audioManagerController.toggleAlertsMute()

    fun setMasterVolume(volume: Float) = audioManagerController.setMasterVolume(volume)

    fun addAudioUris(uris: List<Uri>) = audioManagerController.addAudioUris(uris)
    fun removeTrack(trackId: String) = audioManagerController.removeTrack(trackId)
    fun playTrack(track: AudioTrackItem) = audioManagerController.playTrack(track)
    fun togglePlayPause() = audioManagerController.togglePlayPause()
    fun nextTrack() = audioManagerController.nextTrack()
    fun previousTrack() = audioManagerController.previousTrack()
    fun toggleLooping() = audioManagerController.toggleLooping()
    fun toggleAudioDucking() = audioManagerController.toggleAudioDucking()

    fun selectMicDevice(device: AudioInputDevice) = micController.selectDevice(device)
    fun refreshMicDevices() = micController.refreshDevices()
    fun toggleMicTest() {
        if (isTestingMic.value) {
            micController.stopMicTest()
        } else {
            micController.startMicTest()
        }
    }

    override fun onCleared() {
        super.onCleared()
        micController.stopMicTest()
    }
}
