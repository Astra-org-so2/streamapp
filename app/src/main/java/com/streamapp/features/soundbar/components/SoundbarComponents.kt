package com.streamapp.features.soundbar.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamapp.core.broadcaster.audio.AudioInputDevice
import com.streamapp.core.broadcaster.audio.AudioTrackItem
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
import kotlin.math.roundToInt

@Composable
fun SoundbarMicDeviceSection(
    selectedMicDevice: AudioInputDevice?,
    availableMicDevices: List<AudioInputDevice>,
    isTestingMic: Boolean,
    micLevel: Float,
    micDb: Int,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (AudioInputDevice) -> Unit,
    onToggleTestMic: () -> Unit
) {
    var showDeviceMenu by remember { mutableStateOf(false) }

    IosCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IosBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            selectedMicDevice?.isBluetooth == true -> Icons.Default.BluetoothAudio
                            selectedMicDevice?.isUsb == true -> Icons.Default.Usb
                            selectedMicDevice?.isWired == true -> Icons.Default.Headphones
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Устройство микрофона", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = IosLabelPrimary)
                    Text(selectedMicDevice?.name ?: "Встроенный микрофон", style = MaterialTheme.typography.bodySmall, color = IosBlue, maxLines = 1)
                }
            }

            Box {
                Surface(
                    onClick = {
                        onRefreshDevices()
                        showDeviceMenu = true
                    },
                    shape = CircleShape,
                    color = IosBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, IosBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Сменить", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = IosBlue, modifier = Modifier.size(16.dp))
                    }
                }

                DropdownMenu(
                    expanded = showDeviceMenu,
                    onDismissRequest = { showDeviceMenu = false },
                    modifier = Modifier.background(IosCardElevated)
                ) {
                    availableMicDevices.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${device.name}${if (device.isBluetooth) " (BT)" else if (device.isUsb) " (USB)" else ""}",
                                    color = if (device.id == selectedMicDevice?.id) IosBlue else IosLabelPrimary
                                )
                            },
                            onClick = {
                                onSelectDevice(device)
                                showDeviceMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when {
                                        device.isBluetooth -> Icons.Default.BluetoothAudio
                                        device.isUsb -> Icons.Default.Usb
                                        device.isWired -> Icons.Default.Headphones
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = null,
                                    tint = if (device.id == selectedMicDevice?.id) IosBlue else IosLabelSecondary
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Live VU-Meter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onToggleTestMic,
                shape = RoundedCornerShape(8.dp),
                color = if (isTestingMic) IosGreen.copy(alpha = 0.2f) else IosCardElevated,
                border = BorderStroke(0.5.dp, if (isTestingMic) IosGreen else IosGlassBorder),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isTestingMic) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isTestingMic) IosGreen else IosLabelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isTestingMic) "VU Тест" else "Проверить",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isTestingMic) IosGreen else IosLabelPrimary
                    )
                }
            }

            // VU Meter Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(IosCardElevated)
            ) {
                val animatedLevel by animateFloatAsState(
                    targetValue = if (isTestingMic) micLevel else 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "vu"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedLevel.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(IosGreen, IosOrange, IosRed)
                            )
                        )
                )
            }

            Text(
                text = if (isTestingMic) "$micDb dB" else "-- dB",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isTestingMic && micDb > -6) IosRed else IosLabelSecondary,
                modifier = Modifier.width(44.dp)
            )
        }
    }
}

@Composable
fun SoundbarVoiceDspSection(
    isNoiseSuppressionEnabled: Boolean,
    isEchoCancellationEnabled: Boolean,
    isAudioDuckingEnabled: Boolean,
    noiseGateThresholdDb: Int,
    noiseSuppressionLevel: String,
    onToggleNoiseSuppression: () -> Unit,
    onToggleEchoCancellation: () -> Unit,
    onToggleAudioDucking: () -> Unit,
    onNoiseGateThresholdChange: (Int) -> Unit,
    onNoiseSuppressionLevelChange: (String) -> Unit
) {
    IosCard {
        Text("Обработка голоса (DSP)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
        Spacer(Modifier.height(8.dp))

        // Noise Suppression Switch Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Шумоподавление (NS)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                Text("Устранение шума вентиляторов и кликов", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            }
            IosSwitch(
                checked = isNoiseSuppressionEnabled,
                onCheckedChange = { onToggleNoiseSuppression() }
            )
        }

        if (isNoiseSuppressionEnabled) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Интенсивность фильтрации", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                Spacer(Modifier.height(6.dp))
                IosSegmentedControl(
                    items = listOf("Мягкий", "Студийный", "Агрессивный"),
                    selectedItem = noiseSuppressionLevel,
                    onItemSelected = onNoiseSuppressionLevelChange
                )
            }
        }

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Echo Cancellation Switch Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Эхоподавление (AEC)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                Text("Подавление звука игры из динамиков в микрофон", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            }
            IosSwitch(
                checked = isEchoCancellationEnabled,
                onCheckedChange = { onToggleEchoCancellation() }
            )
        }

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Audio Ducking Switch Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Авто-приглушение (Audio Ducking)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                Text("Снижать громкость игры/музыки, когда вы говорите", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            }
            IosSwitch(
                checked = isAudioDuckingEnabled,
                onCheckedChange = { onToggleAudioDucking() }
            )
        }

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Noise Gate Slider
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Порог гейта (Noise Gate)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = IosLabelPrimary)
                Text("$noiseGateThresholdDb dB", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = IosPurple)
            }
            Text("Микрофон открывается только если голос громче порога", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            Spacer(Modifier.height(6.dp))
            IosControlSlider(
                value = noiseGateThresholdDb.toFloat(),
                onValueChange = { onNoiseGateThresholdChange(it.roundToInt()) },
                valueRange = -60f..-10f,
                activeColor = IosPurple
            )
        }
    }
}

@Composable
fun SoundbarChannelsSection(
    micVolume: Float,
    isMicMuted: Boolean,
    gameVolume: Float,
    isGameMuted: Boolean,
    musicVolume: Float,
    isMusicMuted: Boolean,
    alertsVolume: Float,
    isAlertsMuted: Boolean,
    onMicVolumeChange: (Float) -> Unit,
    onToggleMicMute: () -> Unit,
    onGameVolumeChange: (Float) -> Unit,
    onToggleGameMute: () -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onToggleMusicMute: () -> Unit,
    onAlertsVolumeChange: (Float) -> Unit,
    onToggleAlertsMute: () -> Unit
) {
    IosCard {
        Text("Микшер каналов", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
        Spacer(Modifier.height(8.dp))

        // 1. Microphone Channel
        SoundChannelRow(
            icon = Icons.Default.Mic,
            title = "Микрофон",
            subtitle = "Голос стримера",
            volume = micVolume,
            isMuted = isMicMuted,
            color = IosBlue,
            onVolumeChange = onMicVolumeChange,
            onToggleMute = onToggleMicMute
        )

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))

        // 2. Game Audio Channel
        SoundChannelRow(
            icon = Icons.Default.SportsEsports,
            title = "Звук игры",
            subtitle = "Внутреннее аудио экрана",
            volume = gameVolume,
            isMuted = isGameMuted,
            color = IosGreen,
            onVolumeChange = onGameVolumeChange,
            onToggleMute = onToggleGameMute
        )

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))

        // 3. Background Music Channel
        SoundChannelRow(
            icon = Icons.Default.MusicNote,
            title = "Фоновая музыка",
            subtitle = "BGM саундтрек",
            volume = musicVolume,
            isMuted = isMusicMuted,
            color = IosPurple,
            onVolumeChange = onMusicVolumeChange,
            onToggleMute = onToggleMusicMute
        )

        HorizontalDivider(color = IosGlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))

        // 4. Alerts / Overlays Audio Channel
        SoundChannelRow(
            icon = Icons.Default.NotificationsActive,
            title = "Алерты и Донаты",
            subtitle = "Звуки оверлеев и виджетов",
            volume = alertsVolume,
            isMuted = isAlertsMuted,
            color = IosOrange,
            onVolumeChange = onAlertsVolumeChange,
            onToggleMute = onToggleAlertsMute
        )
    }
}

@Composable
fun SoundChannelRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    volume: Float,
    isMuted: Boolean,
    color: Color,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isMuted) IosRed.copy(alpha = 0.2f) else color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else icon,
                contentDescription = null,
                tint = if (isMuted) IosRed else color,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = IosLabelPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                }
                Text(
                    text = if (isMuted) "MUTE" else "${(volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isMuted) IosRed else color
                )
            }

            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                enabled = !isMuted,
                valueRange = 0f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = IosCardElevated,
                    disabledThumbColor = IosLabelTertiary,
                    disabledActiveTrackColor = IosLabelTertiary
                )
            )
        }

        Spacer(Modifier.width(12.dp))

        // Mute Toggle Icon Button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMuted) IosRed.copy(alpha = 0.15f) else IosCardElevated)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Mute",
                tint = if (isMuted) IosRed else IosLabelPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SoundbarMusicPlayerSection(
    playlist: List<AudioTrackItem>,
    currentTrack: AudioTrackItem?,
    isPlaying: Boolean,
    isLooping: Boolean,
    onPickMusic: () -> Unit,
    onPlayTrack: (AudioTrackItem) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onRemoveTrack: (String) -> Unit
) {
    IosCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Фоновый BGM плеер", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
                Text(
                    text = currentTrack?.let { "Играет: ${it.title}" } ?: "Трек не выбран",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlaying) IosGreen else IosLabelSecondary,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Loop toggle
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLooping) IosPurple.copy(alpha = 0.2f) else IosCardElevated)
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLooping) IosPurple else IosLabelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) IosGreen.copy(alpha = 0.2f) else IosCardElevated)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = if (isPlaying) IosGreen else IosLabelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Add Music Button
                IconButton(
                    onClick = onPickMusic,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(IosBlue.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Track",
                        tint = IosBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosCardElevated)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Плейлист пуст. Нажмите '+' чтобы добавить MP3 / WAV",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosLabelTertiary
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                playlist.forEach { track ->
                    val isCurrent = track.id == currentTrack?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) IosPurple.copy(alpha = 0.15f) else IosCardElevated)
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isCurrent && isPlaying) Icons.Default.GraphicEq else Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = if (isCurrent) IosPurple else IosLabelSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isCurrent) IosPurple else IosLabelPrimary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = IosLabelTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
