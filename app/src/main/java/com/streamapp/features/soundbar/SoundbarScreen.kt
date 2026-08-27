package com.streamapp.features.soundbar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
import com.streamapp.features.soundbar.components.SoundbarChannelsSection
import com.streamapp.features.soundbar.components.SoundbarMicDeviceSection
import com.streamapp.features.soundbar.components.SoundbarMusicPlayerSection
import com.streamapp.features.soundbar.components.SoundbarVoiceDspSection
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundbarScreen(viewModel: SoundbarViewModel = hiltViewModel()) {
    val micVolume by viewModel.micVolume.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()

    val gameVolume by viewModel.gameVolume.collectAsState()
    val isGameMuted by viewModel.isGameMuted.collectAsState()

    val musicVolume by viewModel.musicVolume.collectAsState()
    val isMusicMuted by viewModel.isMusicMuted.collectAsState()

    val alertsVolume by viewModel.alertsVolume.collectAsState()
    val isAlertsMuted by viewModel.isAlertsMuted.collectAsState()

    val masterVolume by viewModel.masterVolume.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()

    val micDevices by viewModel.availableMicDevices.collectAsState()
    val selectedMicDevice by viewModel.selectedMicDevice.collectAsState()
    val isTestingMic by viewModel.isTestingMic.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val micDb by viewModel.micDb.collectAsState()
    val isAudioDuckingEnabled by viewModel.isAudioDuckingEnabled.collectAsState()

    val isNoiseSuppressionEnabled by viewModel.isNoiseSuppressionEnabled.collectAsState()
    val isEchoCancellationEnabled by viewModel.isEchoCancellationEnabled.collectAsState()
    val noiseGateThresholdDb by viewModel.noiseGateThresholdDb.collectAsState()
    val noiseSuppressionLevel by viewModel.noiseSuppressionLevel.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addAudioUris(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // iOS Header
        IosHeader(
            title = "Аудиомикшер",
            subtitle = "Управление балансом, микрофоном и музыкой"
        )

        // 1. Microphone Input Device Selector & Live VU Test
        SoundbarMicDeviceSection(
            selectedMicDevice = selectedMicDevice,
            availableMicDevices = micDevices,
            isTestingMic = isTestingMic,
            micLevel = micLevel,
            micDb = micDb,
            onRefreshDevices = { viewModel.refreshMicDevices() },
            onSelectDevice = { viewModel.selectMicDevice(it) },
            onToggleTestMic = { viewModel.toggleMicTest() }
        )

        // 2. Voice DSP Section (NS, AEC, Ducking, Noise Gate)
        SoundbarVoiceDspSection(
            isNoiseSuppressionEnabled = isNoiseSuppressionEnabled,
            isEchoCancellationEnabled = isEchoCancellationEnabled,
            isAudioDuckingEnabled = isAudioDuckingEnabled,
            noiseGateThresholdDb = noiseGateThresholdDb,
            noiseSuppressionLevel = noiseSuppressionLevel,
            onToggleNoiseSuppression = { viewModel.toggleNoiseSuppression() },
            onToggleEchoCancellation = { viewModel.toggleEchoCancellation() },
            onToggleAudioDucking = { viewModel.toggleAudioDucking() },
            onNoiseGateThresholdChange = { viewModel.setNoiseGateThreshold(it) },
            onNoiseSuppressionLevelChange = { viewModel.setNoiseSuppressionLevel(it) }
        )

        // 3. Audio Channels Mixer
        SoundbarChannelsSection(
            micVolume = micVolume,
            isMicMuted = isMicMuted,
            gameVolume = gameVolume,
            isGameMuted = isGameMuted,
            musicVolume = musicVolume,
            isMusicMuted = isMusicMuted,
            alertsVolume = alertsVolume,
            isAlertsMuted = isAlertsMuted,
            onMicVolumeChange = { viewModel.setMicVolume(it) },
            onToggleMicMute = { viewModel.toggleMicMute() },
            onGameVolumeChange = { viewModel.setGameVolume(it) },
            onToggleGameMute = { viewModel.toggleGameMute() },
            onMusicVolumeChange = { viewModel.setMusicVolume(it) },
            onToggleMusicMute = { viewModel.toggleMusicMute() },
            onAlertsVolumeChange = { viewModel.setAlertsVolume(it) },
            onToggleAlertsMute = { viewModel.toggleAlertsMute() }
        )

        // 4. Master Volume
        IosCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Общая громкость стрима (Master)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = IosLabelPrimary)
                Text("${(masterVolume * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = IosBlue)
            }
            Slider(
                value = masterVolume,
                onValueChange = { viewModel.setMasterVolume(it) },
                valueRange = 0f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = IosBlue,
                    activeTrackColor = IosBlue,
                    inactiveTrackColor = IosCardElevated
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // 5. Background BGM Music Player
        SoundbarMusicPlayerSection(
            playlist = playlist,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            isLooping = isLooping,
            onPickMusic = { audioPickerLauncher.launch("audio/*") },
            onPlayTrack = { viewModel.playTrack(it) },
            onTogglePlay = { viewModel.togglePlayPause() },
            onToggleLoop = { viewModel.toggleLooping() },
            onRemoveTrack = { viewModel.removeTrack(it) }
        )

        Spacer(Modifier.height(80.dp))
    }
}
