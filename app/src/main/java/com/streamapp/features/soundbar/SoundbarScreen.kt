package com.streamapp.features.soundbar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamapp.core.broadcaster.audio.AudioInputDevice
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
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

    var showDeviceMenu by remember { mutableStateOf(false) }

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

                // Device Dropdown
                Box {
                    Surface(
                        onClick = {
                            viewModel.refreshMicDevices()
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
                        modifier = Modifier
                            .widthIn(min = 280.dp)
                            .background(IosCardElevated)
                    ) {
                        micDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                device.isBluetooth -> Icons.Default.BluetoothAudio
                                                device.isUsb -> Icons.Default.Usb
                                                device.isWired -> Icons.Default.Headphones
                                                else -> Icons.Default.Mic
                                            },
                                            contentDescription = null,
                                            tint = if (device.id == selectedMicDevice?.id) IosBlue else IosLabelSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = device.name,
                                            color = if (device.id == selectedMicDevice?.id) IosBlue else IosLabelPrimary,
                                            fontWeight = if (device.id == selectedMicDevice?.id) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectMicDevice(device)
                                    showDeviceMenu = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Live VU Meter & Test Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Тест чувствительности", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                        Text(
                            text = if (isTestingMic) "$micDb dB" else "Нажмите Тест",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isTestingMic) (if (micLevel > 0.8f) IosRed else if (micLevel > 0.4f) IosGreen else IosBlue) else IosLabelTertiary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Animated Multi-segment VU Bar
                    val animatedLevel by animateFloatAsState(
                        targetValue = if (isTestingMic) micLevel else 0f,
                        animationSpec = spring(stiffness = 800f),
                        label = "vuMeter"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(IosCardElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedLevel.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(IosGreen, IosYellow, IosRed)
                                    )
                                )
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Surface(
                    onClick = { viewModel.toggleMicTest() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTestingMic) IosRed.copy(alpha = 0.2f) else IosBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, if (isTestingMic) IosRed else IosBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTestingMic) Icons.Default.Stop else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isTestingMic) IosRed else IosBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isTestingMic) "Стоп" else "Тест",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isTestingMic) IosRed else IosBlue
                        )
                    }
                }
            }
        }

        // 2. Noise Suppression & Voice Cleaner DSP Card
        IosCard {
            Text(
                text = "ШУМОПОДАВЛЕНИЕ И ОБРАБОТКА ГОЛОСА",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosLabelSecondary
            )
            Spacer(Modifier.height(12.dp))

            // Noise Suppression Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NoiseControlOff, contentDescription = null, tint = IosGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Активное шумоподавление", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("Фильтрация шума кулеров, клавиатуры и фона", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                    }
                }
                IosSwitch(
                    checked = isNoiseSuppressionEnabled,
                    onCheckedChange = { viewModel.toggleNoiseSuppression() }
                )
            }

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Echo Cancellation Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SurroundSound, contentDescription = null, tint = IosBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Эхоподавление (AEC)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("Убирает эхо игры и динамиков из микрофона", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                    }
                }
                IosSwitch(
                    checked = isEchoCancellationEnabled,
                    onCheckedChange = { viewModel.toggleEchoCancellation() }
                )
            }

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Noise Gate Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Порог отсечения (Noise Gate)", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                Text("$noiseGateThresholdDb dB", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosGreen)
            }
            Spacer(Modifier.height(6.dp))
            IosControlSlider(
                value = noiseGateThresholdDb.toFloat(),
                onValueChange = { viewModel.setNoiseGateThreshold(it.roundToInt()) },
                valueRange = -50f..-20f,
                activeColor = IosGreen
            )

            Spacer(Modifier.height(12.dp))

            // Noise Suppression Intensity Preset
            Text("Интенсивность фильтрации", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            Spacer(Modifier.height(6.dp))
            IosSegmentedControl(
                items = listOf("Мягкий", "Студийный", "Агрессивный"),
                selectedItem = noiseSuppressionLevel,
                onItemSelected = { viewModel.setNoiseSuppressionLevel(it) }
            )
        }

        // 3. Apple Music Style Player Card (Persistent Audio Files)
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(IosPink, IosPurple))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Фоновая музыка",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = IosLabelPrimary
                        )
                        Text(
                            text = if (isPlaying) "Воспроизводится на стриме" else "Плеер на паузе",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) IosGreen else IosLabelSecondary
                        )
                    }
                }

                Surface(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    shape = CircleShape,
                    color = IosPink.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, IosPink.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = IosPink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Треки", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosPink)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Now Playing Glass Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = currentTrack?.title ?: "Треки сохранены на устройстве. Добавьте файлы через «+ Треки».",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (currentTrack != null) IosLabelPrimary else IosLabelSecondary,
                        maxLines = 1
                    )

                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.previousTrack() }, enabled = playlist.isNotEmpty()) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = IosLabelPrimary)
                        }

                        Surface(
                            onClick = { viewModel.togglePlayPause() },
                            enabled = playlist.isNotEmpty(),
                            shape = CircleShape,
                            color = IosPink,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.nextTrack() }, enabled = playlist.isNotEmpty()) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = IosLabelPrimary)
                        }

                        IconButton(onClick = { viewModel.toggleLooping() }) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (isLooping) IosPink else IosLabelTertiary
                            )
                        }
                    }
                }
            }

            // Audio Ducking Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(IosPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolumeDown, contentDescription = null, tint = IosPurple, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Авто-приглушение (Audio Ducking)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = IosLabelPrimary)
                        Text("Глушит музыку на 70%, когда вы говорите", style = MaterialTheme.typography.labelSmall, color = IosLabelSecondary)
                    }
                }
                IosSwitch(
                    checked = isAudioDuckingEnabled,
                    onCheckedChange = { viewModel.toggleAudioDucking() }
                )
            }

            // Playlist items
            if (playlist.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Сохраненные треки (${playlist.size}):", style = MaterialTheme.typography.labelSmall, color = IosLabelSecondary)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    playlist.forEach { track ->
                        val isCurrent = track.id == currentTrack?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) IosPink.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { viewModel.playTrack(track) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isCurrent) IosPink else IosLabelSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isCurrent) IosPink else IosLabelPrimary,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeTrack(track.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = IosLabelTertiary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Multi-Channel Audio Faders
        IosCard {
            Text(
                text = "КАНАЛЫ ЗВУКА (AUDIO FADERS)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosLabelSecondary
            )
            Spacer(Modifier.height(12.dp))

            // Mic
            IosAudioChannelRow(
                title = "Микрофон",
                subtitle = if (micVolume > 1.0f) "Усиление +${((micVolume - 1f) * 6).roundToInt()} dB" else "Основной голос",
                icon = Icons.Default.Mic,
                iconBg = IosBlue,
                volume = micVolume,
                isMuted = isMicMuted,
                maxRange = 2.0f,
                activeColor = IosBlue,
                onVolumeChange = { viewModel.setMicVolume(it) },
                onToggleMute = { viewModel.toggleMicMute() }
            )

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Game Sound
            IosAudioChannelRow(
                title = "Звук Игры / Экрана",
                subtitle = "Внутренний звук смартфона",
                icon = Icons.Default.Gamepad,
                iconBg = IosPurple,
                volume = gameVolume,
                isMuted = isGameMuted,
                activeColor = IosPurple,
                onVolumeChange = { viewModel.setGameVolume(it) },
                onToggleMute = { viewModel.toggleGameMute() }
            )

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Music
            IosAudioChannelRow(
                title = "Фоновая Музыка",
                subtitle = "Громкость плеера",
                icon = Icons.Default.MusicNote,
                iconBg = IosPink,
                volume = musicVolume,
                isMuted = isMusicMuted,
                activeColor = IosPink,
                onVolumeChange = { viewModel.setMusicVolume(it) },
                onToggleMute = { viewModel.toggleMusicMute() }
            )

            HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Alerts
            IosAudioChannelRow(
                title = "Алерты и Донаты",
                subtitle = "Звуки виджетов",
                icon = Icons.Default.NotificationsActive,
                iconBg = IosOrange,
                volume = alertsVolume,
                isMuted = isAlertsMuted,
                activeColor = IosOrange,
                onVolumeChange = { viewModel.setAlertsVolume(it) },
                onToggleMute = { viewModel.toggleAlertsMute() }
            )
        }

        // 4. Master Output Fader Card
        IosCard(
            backgroundColor = IosCardElevated
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Мастер-Выход Стрима",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = IosLabelPrimary
                        )
                        Text(
                            text = "Итоговая громкость для зрителей",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosLabelSecondary
                        )
                    }
                }
                Text(
                    text = "${(masterVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = IosGreen
                )
            }

            Spacer(Modifier.height(10.dp))

            IosControlSlider(
                value = masterVolume,
                onValueChange = { viewModel.setMasterVolume(it) },
                activeColor = IosGreen
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IosAudioChannelRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    volume: Float,
    isMuted: Boolean,
    maxRange: Float = 1.0f,
    activeColor: Color = IosBlue,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMuted) IosRed.copy(alpha = 0.2f) else iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else icon,
                        contentDescription = null,
                        tint = if (isMuted) IosRed else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isMuted) "MUTE" else "${(volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isMuted) IosRed else activeColor
                )

                Surface(
                    onClick = onToggleMute,
                    shape = CircleShape,
                    color = if (isMuted) IosRed.copy(alpha = 0.15f) else IosCardElevated,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (isMuted) IosRed else IosLabelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        IosControlSlider(
            value = if (isMuted) 0f else volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..maxRange,
            activeColor = activeColor,
            enabled = !isMuted
        )
    }
}
