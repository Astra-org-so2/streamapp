package com.streamapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.designsystem.components.IosCard
import com.streamapp.core.designsystem.components.IosControlSlider
import com.streamapp.core.designsystem.components.IosHeader
import com.streamapp.core.designsystem.components.IosSettingRow
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun updateVideoBitrate(bitrate: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(videoBitrate = bitrate))
        }
    }

    fun updateAudioBitrate(bitrate: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(audioBitrate = bitrate))
        }
    }

    fun toggleFeature(featureName: String) {
        viewModelScope.launch {
            val current = settings.value
            val updated = when (featureName) {
                "Heatmap" -> current.copy(touchHeatmapEnabled = !current.touchHeatmapEnabled)
                "Auto-Clip" -> current.copy(autoClipEnabled = !current.autoClipEnabled)
                "Privacy Guard" -> current.copy(backgroundBlurEnabled = !current.backgroundBlurEnabled)
                else -> current
            }
            settingsRepository.updateSettings(updated)
        }
    }

    fun updateHeatmapDecay(timeMs: Long) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(heatmapDecayTimeMs = timeMs))
        }
    }

    fun updateAutoClipDuration(durationS: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(autoClipDurationS = durationS))
        }
    }

    fun updatePrivacyGuardBlur(intensity: Float) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings.value.copy(privacyGuardBlurIntensity = intensity))
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        IosHeader(
            title = "Настройки",
            subtitle = "Параметры стрима и расширенные функции"
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Group 1: Video & Audio Output
            item {
                Text(
                    text = "ПАРАМЕТРЫ ТРАНСЛЯЦИИ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                IosCard {
                    // Video Bitrate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Битрейт Видео", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("${settings.videoBitrate} kbps", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    IosControlSlider(
                        value = settings.videoBitrate.toFloat(),
                        onValueChange = { viewModel.updateVideoBitrate(it.toInt()) },
                        valueRange = 1000f..10000f,
                        activeColor = IosBlue
                    )

                    HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Audio Bitrate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Битрейт Аудио", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("${settings.audioBitrate} kbps", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosPurple)
                    }
                    Spacer(Modifier.height(4.dp))
                    IosControlSlider(
                        value = settings.audioBitrate.toFloat(),
                        onValueChange = { viewModel.updateAudioBitrate(it.toInt()) },
                        valueRange = 64f..320f,
                        activeColor = IosPurple
                    )
                }
            }

            // Group 2: Unique Features (iOS Settings List)
            item {
                Text(
                    text = "ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                IosCard {
                    // Heatmap
                    IosSettingRow(
                        title = "Тепловая карта кликов",
                        subtitle = "Подсветка нажатий на экране",
                        icon = Icons.Default.TouchApp,
                        iconBgColor = IosRed,
                        trailing = {
                            Switch(
                                checked = settings.touchHeatmapEnabled,
                                onCheckedChange = { viewModel.toggleFeature("Heatmap") },
                                colors = SwitchDefaults.colors(checkedTrackColor = IosGreen)
                            )
                        }
                    )

                    HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // Auto Clip
                    IosSettingRow(
                        title = "Авто-клипы (Hype Detector)",
                        subtitle = "Запись ярких моментов по громкости",
                        icon = Icons.Default.AutoAwesome,
                        iconBgColor = IosOrange,
                        trailing = {
                            Switch(
                                checked = settings.autoClipEnabled,
                                onCheckedChange = { viewModel.toggleFeature("Auto-Clip") },
                                colors = SwitchDefaults.colors(checkedTrackColor = IosGreen)
                            )
                        }
                    )

                    HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // Privacy Guard
                    IosSettingRow(
                        title = "Screen Privacy Guard",
                        subtitle = "Скрытие уведомлений и паролей",
                        icon = Icons.Default.Shield,
                        iconBgColor = IosBlue,
                        trailing = {
                            Switch(
                                checked = settings.backgroundBlurEnabled,
                                onCheckedChange = { viewModel.toggleFeature("Privacy Guard") },
                                colors = SwitchDefaults.colors(checkedTrackColor = IosGreen)
                            )
                        }
                    )
                }
            }

            // Group 3: Fine Tuning
            item {
                Text(
                    text = "ТОНКАЯ НАСТРОЙКА АЛГОРИТМОВ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                IosCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Время затухания клика", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("${settings.heatmapDecayTimeMs} ms", style = MaterialTheme.typography.labelMedium, color = IosRed)
                    }
                    Spacer(Modifier.height(4.dp))
                    IosControlSlider(
                        value = settings.heatmapDecayTimeMs.toFloat(),
                        onValueChange = { viewModel.updateHeatmapDecay(it.toLong()) },
                        valueRange = 500f..5000f,
                        activeColor = IosRed
                    )

                    HorizontalDivider(color = IosGlassBorder, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Длительность клипа", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = IosLabelPrimary)
                        Text("${settings.autoClipDurationS} сек", style = MaterialTheme.typography.labelMedium, color = IosOrange)
                    }
                    Spacer(Modifier.height(4.dp))
                    IosControlSlider(
                        value = settings.autoClipDurationS.toFloat(),
                        onValueChange = { viewModel.updateAutoClipDuration(it.toInt()) },
                        valueRange = 10f..120f,
                        activeColor = IosOrange
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
