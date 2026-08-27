package com.streamapp.features.scene

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.theme.AccentCyan
import com.streamapp.core.designsystem.theme.SurfaceDark
import com.streamapp.core.designsystem.theme.TextPrimary
import com.streamapp.core.designsystem.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SceneLayerCanvas(
    layers: List<SceneLayerEntity>,
    isEditMode: Boolean,
    onUpdateLayerBounds: (layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit,
    onUpdateLayerAlpha: (layerId: String, alpha: Float) -> Unit,
    onToggleLock: (layerId: String, currentLocked: Boolean) -> Unit,
    onCenterLayer: (layerId: String) -> Unit,
    onFitToScreen: (layerId: String) -> Unit,
    onDuplicateLayer: (layer: SceneLayerEntity) -> Unit,
    onDeleteLayer: (layerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLayerId by remember { mutableStateOf<String?>(null) }

    // Clear selection if edit mode is exited
    LaunchedEffect(isEditMode) {
        if (!isEditMode) selectedLayerId = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures {
                        selectedLayerId = null // Tap outside deselects
                    }
                }
            }
    ) {
        val parentWidth = maxWidth.value
        val parentHeight = maxHeight.value

        layers.filter { it.isVisible }.forEach { layer ->
            val isSelected = isEditMode && (layer.id == selectedLayerId)

            key(layer.id) {
                InteractiveLayerItem(
                    layer = layer,
                    isSelected = isSelected,
                    isEditMode = isEditMode,
                    parentWidthDp = parentWidth,
                    parentHeightDp = parentHeight,
                    onSelect = { selectedLayerId = layer.id },
                    onBoundsChanged = { x, y, w, h, scale ->
                        onUpdateLayerBounds(layer.id, x, y, w, h, scale)
                    },
                    onDelete = { onDeleteLayer(layer.id) }
                )
            }
        }

        // Floating Inspector Toolbar for Selected Layer
        val selectedLayer = layers.find { it.id == selectedLayerId }
        AnimatedVisibility(
            visible = isEditMode && selectedLayer != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            if (selectedLayer != null) {
                LayerInspectorBar(
                    layer = selectedLayer,
                    onAlphaChange = { onUpdateLayerAlpha(selectedLayer.id, it) },
                    onToggleLock = { onToggleLock(selectedLayer.id, selectedLayer.isLocked) },
                    onCenter = { onCenterLayer(selectedLayer.id) },
                    onFitScreen = { onFitToScreen(selectedLayer.id) },
                    onDuplicate = { onDuplicateLayer(selectedLayer) },
                    onDelete = {
                        onDeleteLayer(selectedLayer.id)
                        selectedLayerId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun InteractiveLayerItem(
    layer: SceneLayerEntity,
    isSelected: Boolean,
    isEditMode: Boolean,
    parentWidthDp: Float,
    parentHeightDp: Float,
    onSelect: () -> Unit,
    onBoundsChanged: (posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit,
    onDelete: () -> Unit
) {
    var posX by remember(layer.posX) { mutableStateOf(layer.posX * parentWidthDp) }
    var posY by remember(layer.posY) { mutableStateOf(layer.posY * parentHeightDp) }
    var widthDp by remember(layer.width) { mutableStateOf(layer.width * parentWidthDp) }
    var heightDp by remember(layer.height) { mutableStateOf(layer.height * parentHeightDp) }

    Box(
        modifier = Modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .size(widthDp.dp, heightDp.dp)
            .alpha(layer.alpha)
            .then(
                if (isEditMode) {
                    Modifier
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) AccentCyan else Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onSelect)
                        .then(
                            if (!layer.isLocked) {
                                Modifier.pointerInput(layer.id, isSelected) {
                                    detectDragGestures(
                                        onDragStart = { onSelect() },
                                        onDragEnd = {
                                            val normX = (posX / parentWidthDp).coerceIn(0f, 0.95f)
                                            val normY = (posY / parentHeightDp).coerceIn(0f, 0.95f)
                                            val normW = (widthDp / parentWidthDp).coerceIn(0.1f, 1.0f)
                                            val normH = (heightDp / parentHeightDp).coerceIn(0.05f, 1.0f)
                                            onBoundsChanged(normX, normY, normW, normH, layer.scale)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        posX = (posX + dragAmount.x).coerceIn(0f, parentWidthDp - 40f)
                                        posY = (posY + dragAmount.y).coerceIn(0f, parentHeightDp - 40f)
                                    }
                                }
                            } else Modifier
                        )
                } else Modifier
            )
    ) {
        // Layer Content Rendering
        when (layer.type) {
            LayerType.WEB -> {
                WebWidgetLiveRenderer(layer = layer)
            }
            LayerType.TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = layer.content,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            LayerType.IMAGE -> {
                AsyncImage(
                    model = layer.content,
                    contentDescription = layer.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LayerType.CAMERA_PIP -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("FaceCam PiP", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Edit Mode Anchors and Resize Handle
        if (isSelected) {
            // Title Badge & Lock Indicator
            Surface(
                color = AccentCyan,
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (layer.isLocked) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black
                    )
                }
            }

            // Quick Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .background(Color(0xFFFF5252), RoundedCornerShape(4.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            // Bottom-Right Corner Resize Drag Handle
            if (!layer.isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .offset(x = 6.dp, y = 6.dp)
                        .clip(CircleShape)
                        .background(AccentCyan)
                        .pointerInput(layer.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val normX = (posX / parentWidthDp).coerceIn(0f, 0.95f)
                                    val normY = (posY / parentHeightDp).coerceIn(0f, 0.95f)
                                    val normW = (widthDp / parentWidthDp).coerceIn(0.1f, 1.0f)
                                    val normH = (heightDp / parentHeightDp).coerceIn(0.05f, 1.0f)
                                    onBoundsChanged(normX, normY, normW, normH, layer.scale)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                widthDp = (widthDp + dragAmount.x).coerceIn(60f, parentWidthDp - posX)
                                heightDp = (heightDp + dragAmount.y).coerceIn(30f, parentHeightDp - posY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Resize Handle",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerInspectorBar(
    layer: SceneLayerEntity,
    onAlphaChange: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onCenter: () -> Unit,
    onFitScreen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark.copy(alpha = 0.95f),
        tonalElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Layer Name & Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Layer Inspector & Transform",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (layer.isLocked) AccentCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF5252))
                    }
                }
            }

            // Opacity / Transparency Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Opacity,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Opacity: ${(layer.alpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.width(100.dp)
                )
                Slider(
                    value = layer.alpha,
                    onValueChange = onAlphaChange,
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan
                    )
                )
            }

            // Quick Alignment Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCenter,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Center", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onFitScreen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fit Full", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
