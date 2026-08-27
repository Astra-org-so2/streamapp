package com.streamapp.features.scene.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
import com.streamapp.features.scene.SceneLayerCanvas
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorTopBar(
    scene: SceneEntity?,
    isLivePreviewMode: Boolean,
    isPreviewActive: Boolean,
    isLandscapeCanvas: Boolean,
    onClose: () -> Unit,
    onTogglePreviewActive: () -> Unit,
    onToggleLivePreviewMode: () -> Unit,
    onToggleOrientation: () -> Unit,
    onAddLayerClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = if (isLivePreviewMode) "Предпросмотр: ${scene?.name ?: "Main"}" else "Студия: ${scene?.name ?: "Main"}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = IosLabelPrimary
                )
                Text(
                    text = if (isLivePreviewMode) "👁 Чистый вид потока без рамок" else "16:9 Stream Canvas Pro",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLivePreviewMode) IosOrange else IosBlue
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IosLabelPrimary)
            }
        },
        actions = {
            // Live Game Screen Capture Toggle
            Surface(
                onClick = onTogglePreviewActive,
                shape = RoundedCornerShape(12.dp),
                color = if (isPreviewActive) IosGreen.copy(alpha = 0.2f) else IosCardElevated,
                border = BorderStroke(0.5.dp, if (isPreviewActive) IosGreen else IosGlassBorder),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPreviewActive) IosGreen else IosRed)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isPreviewActive) "Игра: Live" else "Захват игры",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isPreviewActive) IosGreen else IosLabelPrimary
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Preview Toggle Button
            Surface(
                onClick = onToggleLivePreviewMode,
                shape = CircleShape,
                color = if (isLivePreviewMode) IosOrange.copy(alpha = 0.2f) else IosCardElevated,
                border = BorderStroke(0.5.dp, if (isLivePreviewMode) IosOrange else IosGlassBorder),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLivePreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                        contentDescription = "Toggle Preview",
                        tint = if (isLivePreviewMode) IosOrange else IosLabelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Rotate Canvas
            Surface(
                onClick = onToggleOrientation,
                shape = CircleShape,
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLandscapeCanvas) Icons.Default.ScreenRotation else Icons.Default.PhoneAndroid,
                        contentDescription = "Rotate Canvas",
                        tint = IosLabelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Add Layer Button
            Surface(
                onClick = onAddLayerClick,
                shape = CircleShape,
                color = IosBlue,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add Layer", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = IosBackground)
    )
}

@Composable
fun SceneEditorCanvasPreview(
    isLandscapeCanvas: Boolean,
    isLivePreviewMode: Boolean,
    isPreviewActive: Boolean,
    previewBitmap: Bitmap?,
    layers: List<SceneLayerEntity>,
    onUpdateLayerBounds: (layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit,
    onUpdateLayerAlpha: (layerId: String, alpha: Float) -> Unit,
    onToggleLock: (layerId: String, currentLocked: Boolean) -> Unit,
    onCenterLayer: (layerId: String) -> Unit,
    onFitToScreen: (layerId: String) -> Unit,
    onDuplicateLayer: (layer: SceneLayerEntity) -> Unit,
    onDeleteLayer: (layerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .aspectRatio(if (isLandscapeCanvas) 16f / 9f else 9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, if (isLivePreviewMode) IosOrange.copy(alpha = 0.5f) else IosGlassBorder, RoundedCornerShape(16.dp))
    ) {
        // Screen background / live game capture
        if (isPreviewActive && previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Screen Capture Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Stylized placeholder background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F14)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = IosLabelTertiary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1080p Stream Canvas",
                        style = MaterialTheme.typography.labelSmall,
                        color = IosLabelTertiary.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Live interactive Scene Layer Canvas
        SceneLayerCanvas(
            layers = layers,
            isEditMode = !isLivePreviewMode,
            onUpdateLayerBounds = onUpdateLayerBounds,
            onUpdateLayerAlpha = onUpdateLayerAlpha,
            onToggleLock = onToggleLock,
            onCenterLayer = onCenterLayer,
            onFitToScreen = onFitToScreen,
            onDuplicateLayer = onDuplicateLayer,
            onDeleteLayer = onDeleteLayer,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SceneLayersTab(
    layers: List<SceneLayerEntity>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (layerId: String, currentVisible: Boolean) -> Unit,
    onToggleLock: (layerId: String, currentLocked: Boolean) -> Unit,
    onEditLayer: (SceneLayerEntity) -> Unit,
    onDuplicateLayer: (SceneLayerEntity) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onAddLayerClick: () -> Unit
) {
    if (layers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("В сцене пока нет слоёв", style = MaterialTheme.typography.bodyMedium, color = IosLabelSecondary)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAddLayerClick,
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить первый виджет")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(layers, key = { it.id }) { layer ->
                val isSelected = layer.id == selectedLayerId

                IosCard(
                    modifier = Modifier.clickable { onSelectLayer(layer.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (layer.type) {
                                            LayerType.WEB -> IosBlue.copy(alpha = 0.2f)
                                            LayerType.TEXT -> IosGreen.copy(alpha = 0.2f)
                                            LayerType.IMAGE -> IosPurple.copy(alpha = 0.2f)
                                            LayerType.CAMERA_PIP -> IosOrange.copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (layer.type) {
                                        LayerType.WEB -> Icons.Default.Language
                                        LayerType.TEXT -> Icons.Default.TextFields
                                        LayerType.IMAGE -> Icons.Default.Image
                                        LayerType.CAMERA_PIP -> Icons.Default.Videocam
                                    },
                                    contentDescription = null,
                                    tint = when (layer.type) {
                                        LayerType.WEB -> IosBlue
                                        LayerType.TEXT -> IosGreen
                                        LayerType.IMAGE -> IosPurple
                                        LayerType.CAMERA_PIP -> IosOrange
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = layer.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) IosBlue else IosLabelPrimary
                                )
                                Text(
                                    text = when (layer.type) {
                                        LayerType.WEB -> "Веб-виджет: ${layer.content}"
                                        LayerType.TEXT -> "Текст: ${layer.content}"
                                        LayerType.IMAGE -> "Изображение"
                                        LayerType.CAMERA_PIP -> "Камера PiP"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosLabelSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Lock toggle
                            IconButton(onClick = { onToggleLock(layer.id, layer.isLocked) }) {
                                Icon(
                                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    tint = if (layer.isLocked) IosOrange else IosLabelSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Visibility toggle
                            IconButton(onClick = { onToggleVisibility(layer.id, layer.isVisible) }) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = if (layer.isVisible) IosGreen else IosLabelSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Edit Content button
                            IconButton(onClick = { onEditLayer(layer) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = IosBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Delete button
                            IconButton(onClick = { onDeleteLayer(layer.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = IosRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScenePropertiesTab(
    selectedLayer: SceneLayerEntity?,
    onUpdateBounds: (posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit,
    onUpdateAlpha: (alpha: Float) -> Unit,
    onCenterLayer: () -> Unit,
    onFitToScreen: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteLayer: () -> Unit
) {
    if (selectedLayer == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Выберите слой во вкладке 'Слои' или нажмите на него в превью", style = MaterialTheme.typography.bodyMedium, color = IosLabelSecondary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IosCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Свойства: ${selectedLayer.name}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
                    Spacer(Modifier.height(12.dp))

                    // Scale Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Масштаб", style = MaterialTheme.typography.bodyMedium, color = IosLabelPrimary)
                        Text("${(selectedLayer.scale * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = IosBlue)
                    }
                    Slider(
                        value = selectedLayer.scale,
                        onValueChange = { onUpdateBounds(selectedLayer.posX, selectedLayer.posY, selectedLayer.width, selectedLayer.height, it) },
                        valueRange = 0.2f..2.5f,
                        colors = SliderDefaults.colors(thumbColor = IosBlue, activeTrackColor = IosBlue)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Alpha Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Прозрачность", style = MaterialTheme.typography.bodyMedium, color = IosLabelPrimary)
                        Text("${(selectedLayer.alpha * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = IosGreen)
                    }
                    Slider(
                        value = selectedLayer.alpha,
                        onValueChange = onUpdateAlpha,
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = IosGreen, activeTrackColor = IosGreen)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCenterLayer,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosCardElevated)
                        ) {
                            Text("По центру", color = IosLabelPrimary)
                        }

                        Button(
                            onClick = onFitToScreen,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosCardElevated)
                        ) {
                            Text("На весь экран", color = IosLabelPrimary)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDuplicateLayer,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosBlue.copy(alpha = 0.2f))
                        ) {
                            Text("Дублировать", color = IosBlue)
                        }

                        Button(
                            onClick = onDeleteLayer,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosRed.copy(alpha = 0.2f))
                        ) {
                            Text("Удалить", color = IosRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SceneTemplatesTab(
    onApplyTemplate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TemplateCard(
            title = "🎮 Игровой стрим (Game Layout)",
            description = "DonationAlerts сверху, FaceCam PiP в правом углу, плашка Live снизу",
            onClick = { onApplyTemplate("game_layout") }
        )

        TemplateCard(
            title = "💬 Разговорный стрим (Chat Layout)",
            description = "Виджет чата справа, алерты донатов и цель сбора слева",
            onClick = { onApplyTemplate("chat_layout") }
        )

        TemplateCard(
            title = "☕ Скоро вернусь (BRB / Timer)",
            description = "Таймер обратного отсчета до начала и соцсети",
            onClick = { onApplyTemplate("brb_layout") }
        )
    }
}

@Composable
private fun TemplateCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    IosCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = IosBlue.copy(alpha = 0.15f),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Применить шаблон",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = IosBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
