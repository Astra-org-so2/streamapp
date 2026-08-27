package com.streamapp.core.broadcaster.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.streamapp.core.database.dao.AudioTrackDao
import com.streamapp.core.database.entity.AudioTrackEntity
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.util.FileStorageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "Local Audio",
    val uri: String,
    val durationMs: Long = 0L
)

@Singleton
class AudioManagerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioTrackDao: AudioTrackDao,
    private val settingsRepository: SettingsRepository,
    val microphoneController: MicrophoneController,
    val audioMixer: AudioMixer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null

    // Channel Volumes & Mute States
    private val _micVolume = MutableStateFlow(1.0f)
    val micVolume: StateFlow<Float> = _micVolume.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _gameVolume = MutableStateFlow(1.0f)
    val gameVolume: StateFlow<Float> = _gameVolume.asStateFlow()

    private val _isGameMuted = MutableStateFlow(false)
    val isGameMuted: StateFlow<Boolean> = _isGameMuted.asStateFlow()

    private val _musicVolume = MutableStateFlow(0.7f)
    val musicVolume: StateFlow<Float> = _musicVolume.asStateFlow()

    private val _isMusicMuted = MutableStateFlow(false)
    val isMusicMuted: StateFlow<Boolean> = _isMusicMuted.asStateFlow()

    private val _alertsVolume = MutableStateFlow(1.0f)
    val alertsVolume: StateFlow<Float> = _alertsVolume.asStateFlow()

    private val _isAlertsMuted = MutableStateFlow(false)
    val isAlertsMuted: StateFlow<Boolean> = _isAlertsMuted.asStateFlow()

    private val _masterVolume = MutableStateFlow(1.0f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    // Audio Ducking (Auto-duck music when speaking)
    private val _isAudioDuckingEnabled = MutableStateFlow(true)
    val isAudioDuckingEnabled: StateFlow<Boolean> = _isAudioDuckingEnabled.asStateFlow()
    private var duckingMultiplier = 1.0f

    // Playlist & Playback State
    private val _playlist = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val playlist: StateFlow<List<AudioTrackItem>> = _playlist.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrackItem?>(null)
    val currentTrack: StateFlow<AudioTrackItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    init {
        // Observe preferred microphone device changes
        scope.launch {
            microphoneController.selectedDevice.collect {
                audioMixer.preferredDevice = microphoneController.getPreferredAudioDeviceInfo()
            }
        }

        // Load initial persisted volume levels from DataStore
        scope.launch {
            val s = settingsRepository.settings.firstOrNull()
            if (s != null) {
                _micVolume.value = s.micVolume
                audioMixer.micVolume = s.micVolume
                _gameVolume.value = s.gameVolume
                audioMixer.gameVolume = s.gameVolume
                _musicVolume.value = s.musicVolume
                audioMixer.musicVolume = s.musicVolume
                _alertsVolume.value = s.alertsVolume
                _masterVolume.value = s.masterVolume
                audioMixer.masterVolume = s.masterVolume

                _isMicMuted.value = s.isMicMuted
                audioMixer.isMicMuted = s.isMicMuted
                _isGameMuted.value = s.isGameMuted
                audioMixer.isGameMuted = s.isGameMuted
                _isMusicMuted.value = s.isMusicMuted
                audioMixer.isMusicMuted = s.isMusicMuted
                _isAlertsMuted.value = s.isAlertsMuted
            }
        }

        // Load persistent audio tracks from Room DB
        scope.launch {
            audioTrackDao.getAllTracks().collect { entities ->
                val trackItems = entities.map {
                    AudioTrackItem(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        uri = it.filePath,
                        durationMs = it.durationMs
                    )
                }
                _playlist.value = trackItems
                if (_currentTrack.value == null && trackItems.isNotEmpty()) {
                    _currentTrack.value = trackItems.first()
                }
            }
        }

        // Monitor mic level for live Audio Ducking
        scope.launch {
            microphoneController.micLevel.collect { level ->
                if (_isAudioDuckingEnabled.value && !_isMicMuted.value) {
                    val isSpeaking = level > 0.06f
                    val target = if (isSpeaking) 0.30f else 1.0f
                    if (target != duckingMultiplier) {
                        duckingMultiplier = target
                        applyMusicPlayerVolume()
                    }
                } else if (duckingMultiplier != 1.0f) {
                    duckingMultiplier = 1.0f
                    applyMusicPlayerVolume()
                }
            }
        }
    }

    private fun persistSettings() {
        scope.launch {
            val current = settingsRepository.settings.firstOrNull() ?: return@launch
            settingsRepository.updateSettings(
                current.copy(
                    micVolume = _micVolume.value,
                    gameVolume = _gameVolume.value,
                    musicVolume = _musicVolume.value,
                    alertsVolume = _alertsVolume.value,
                    masterVolume = _masterVolume.value,
                    isMicMuted = _isMicMuted.value,
                    isGameMuted = _isGameMuted.value,
                    isMusicMuted = _isMusicMuted.value,
                    isAlertsMuted = _isAlertsMuted.value
                )
            )
        }
    }

    fun toggleAudioDucking() {
        _isAudioDuckingEnabled.value = !_isAudioDuckingEnabled.value
        audioMixer.isAudioDuckingEnabled = _isAudioDuckingEnabled.value
        if (!_isAudioDuckingEnabled.value) {
            duckingMultiplier = 1.0f
            applyMusicPlayerVolume()
        }
    }

    fun setMicVolume(vol: Float) {
        _micVolume.value = vol.coerceIn(0.0f, 2.0f)
        audioMixer.micVolume = _micVolume.value
        persistSettings()
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
        audioMixer.isMicMuted = _isMicMuted.value
        persistSettings()
    }

    fun setGameVolume(vol: Float) {
        _gameVolume.value = vol.coerceIn(0.0f, 1.0f)
        audioMixer.gameVolume = _gameVolume.value
        persistSettings()
    }

    fun toggleGameMute() {
        _isGameMuted.value = !_isGameMuted.value
        audioMixer.isGameMuted = _isGameMuted.value
        persistSettings()
    }

    fun setMusicVolume(vol: Float) {
        _musicVolume.value = vol.coerceIn(0.0f, 1.0f)
        audioMixer.musicVolume = _musicVolume.value
        applyMusicPlayerVolume()
        persistSettings()
    }

    fun toggleMusicMute() {
        _isMusicMuted.value = !_isMusicMuted.value
        audioMixer.isMusicMuted = _isMusicMuted.value
        applyMusicPlayerVolume()
        persistSettings()
    }

    fun setAlertsVolume(vol: Float) {
        _alertsVolume.value = vol.coerceIn(0.0f, 1.0f)
        persistSettings()
    }

    fun toggleAlertsMute() {
        _isAlertsMuted.value = !_isAlertsMuted.value
        persistSettings()
    }

    fun setMasterVolume(vol: Float) {
        _masterVolume.value = vol.coerceIn(0.0f, 1.0f)
        audioMixer.masterVolume = _masterVolume.value
        applyMusicPlayerVolume()
        persistSettings()
    }

    private fun applyMusicPlayerVolume() {
        val actualVol = if (_isMusicMuted.value) 0.0f else (_musicVolume.value * _masterVolume.value * duckingMultiplier)
        mediaPlayer?.setVolume(actualVol, actualVol)
    }

    // Music Player Methods
    fun addAudioUris(uris: List<Uri>) {
        scope.launch {
            val entities = mutableListOf<AudioTrackEntity>()
            val currentCount = _playlist.value.size

            uris.forEachIndexed { index, uri ->
                val localPath = FileStorageHelper.copyUriToInternalStorage(context, uri, "audio")
                if (localPath != null) {
                    val rawName = FileStorageHelper.getFileName(context, uri) ?: "Audio Track ${currentCount + index + 1}"
                    val cleanTitle = rawName.substringBeforeLast('.')
                    val entity = AudioTrackEntity(
                        id = UUID.randomUUID().toString(),
                        title = cleanTitle,
                        artist = "Device Audio",
                        filePath = localPath,
                        orderIndex = currentCount + index
                    )
                    entities.add(entity)
                }
            }

            if (entities.isNotEmpty()) {
                audioTrackDao.insertTracks(entities)
                if (_currentTrack.value == null) {
                    val first = entities.first()
                    _currentTrack.value = AudioTrackItem(first.id, first.title, first.artist, first.filePath)
                    playTrack(_currentTrack.value!!)
                }
            }
        }
    }

    fun removeTrack(trackId: String) {
        scope.launch {
            val trackToRemove = _playlist.value.find { it.id == trackId }
            audioTrackDao.deleteTrackById(trackId)
            trackToRemove?.uri?.let { path ->
                try { File(path).delete() } catch (e: Exception) {}
            }
            if (_currentTrack.value?.id == trackId) {
                val remaining = _playlist.value.filter { it.id != trackId }
                if (remaining.isNotEmpty()) {
                    playTrack(remaining.first())
                } else {
                    stopMusic()
                }
            }
        }
    }

    fun playTrack(track: AudioTrackItem) {
        scope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(track.uri))
                    isLooping = _isLooping.value
                    prepare()
                    applyMusicPlayerVolume()
                    start()
                    setOnCompletionListener {
                        if (!_isLooping.value) {
                            nextTrack()
                        }
                    }
                }
                _currentTrack.value = track
                _isPlaying.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.start()
                _isPlaying.value = true
            }
        } ?: run {
            _currentTrack.value?.let { playTrack(it) }
                ?: _playlist.value.firstOrNull()?.let { playTrack(it) }
        }
    }

    fun nextTrack() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _currentTrack.value?.id }
        val nextIndex = (currentIndex + 1) % list.size
        playTrack(list[nextIndex])
    }

    fun previousTrack() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _currentTrack.value?.id }
        val prevIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
        playTrack(list[prevIndex])
    }

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        mediaPlayer?.isLooping = _isLooping.value
    }

    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentTrack.value = null
    }
}
