package com.streamapp.features.scene

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.streamapp.core.broadcaster.screen.ScreenPreviewManager
import com.streamapp.core.broadcaster.screen.ScreenCaptureService
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneLayoutEditorScreen(
    scene: SceneEntity?,
    layers: List<SceneLayerEntity>,
    screenPreviewManager: ScreenPreviewManager? = null,
    onClose: () -> Unit,
    onAddLayer: (type: LayerType, name: String, content: String, width: Float, height: Float, alpha: Float) -> Unit,
    onApplyTemplate: (templateKey: String) -> Unit,
    onUpdateLayerBounds: (layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit,
    onUpdateLayerAlpha: (layerId: String, alpha: Float) -> Unit,
    onUpdateLayerContent: (layerId: String, name: String, content: String) -> Unit = { _, _, _ -> },
    onToggleVisibility: (layerId: String, currentVisible: Boolean) -> Unit,
    onToggleLock: (layerId: String, currentLocked: Boolean) -> Unit,
    onCenterLayer: (layerId: String) -> Unit,
    onFitToScreen: (layerId: String) -> Unit,
    onDuplicateLayer: (layer: SceneLayerEntity) -> Unit,
    onDeleteLayer: (layerId: String) -> Unit
) {
    var selectedLayerId by remember(layers) { mutableStateOf<String?>(layers.firstOrNull()?.id) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Layers, 1 = Properties, 2 = Templates
    var isLandscapeCanvas by remember { mutableStateOf(true) }
    var isLivePreviewMode by remember { mutableStateOf(false) }
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var layerToEdit by remember { mutableStateOf<SceneLayerEntity?>(null) }

    val context = LocalContext.current
    val fallbackPreviewActive = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val fallbackBitmap = remember { kotlinx.coroutines.flow.MutableStateFlow<Bitmap?>(null) }
    val isPreviewActive by (screenPreviewManager?.isPreviewActive ?: fallbackPreviewActive).collectAsState()
    val previewBitmap by (screenPreviewManager?.latestFrameBitmap ?: fallbackBitmap).collectAsState()

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dataIntent = result.data
        if (result.resultCode == Activity.RESULT_OK && dataIntent != null) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_PREVIEW
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, dataIntent)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    Scaffold(
        topBar = {
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
                        onClick = {
                            if (isPreviewActive) {
                                screenPreviewManager?.stopPreview()
                            } else {
                                val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                val captureIntent = if (android.os.Build.VERSION.SDK_INT >= 34) {
                                    val config = android.media.projection.MediaProjectionConfig.createConfigForUserChoice()
                                    mediaProjectionManager.createScreenCaptureIntent(config)
                                } else {
                                    mediaProjectionManager.createScreenCaptureIntent()
                                }
                                screenCaptureLauncher.launch(captureIntent)
                            }
                        },
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
                        onClick = { isLivePreviewMode = !isLivePreviewMode },
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
                        onClick = { isLandscapeCanvas = !isLandscapeCanvas },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IosBackground)
            )
        },
        containerColor = IosBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Section: Crisp 16:9 / 9:16 Stream Canvas with NO dead zones
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLivePreviewMode) Modifier.weight(1f)
                        else Modifier.wrapContentHeight()
                    )
                    .background(IosBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Aspect Ratio Canvas Box
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isLandscapeCanvas) 16f / 9f else 9f / 16f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0F15))
                        .border(
                            width = 1.5.dp,
                            color = if (isLivePreviewMode) IosOrange.copy(alpha = 0.6f) else IosGlassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    val canvasWidth = maxWidth.value
                    val canvasHeight = maxHeight.value

                    // Background: Live Screen/Game Mirror or Ambient Clean Backdrop
                    GameStreamViewportBackdrop(
                        previewBitmap = previewBitmap,
                        isPreviewActive = isPreviewActive,
                        isLivePreview = isLivePreviewMode
                    )

                    // Render All Active Layers
                    layers.filter { it.isVisible }.forEach { layer ->
                        val isSelected = (layer.id == selectedLayerId) && !isLivePreviewMode
                        key(layer.id) {
                            SmoothCanvasLayerItem(
                                layer = layer,
                                isSelected = isSelected,
                                isLivePreview = isLivePreviewMode,
                                canvasWidthDp = canvasWidth,
                                canvasHeightDp = canvasHeight,
                                onSelect = { selectedLayerId = layer.id },
                                onBoundsChanged = { x, y, w, h, s ->
                                    onUpdateLayerBounds(layer.id, x, y, w, h, s)
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Section: Professional Editing Inspector (hidden in clean preview mode)
            if (!isLivePreviewMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = IosCard,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(0.5.dp, IosGlassBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        // Section Segmented Tabs: [Слои] [Настройки] [Пресеты]
                        IosSegmentedControl(
                            items = listOf(0, 1, 2),
                            selectedItem = selectedTab,
                            onItemSelected = { selectedTab = it },
                            itemLabel = {
                                when (it) {
                                    0 -> "📋 Слои (${layers.size})"
                                    1 -> "🎛️ Свойства"
                                    2 -> "✨ Пресеты"
                                    else -> ""
                                }
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        // Tab Contents
                        when (selectedTab) {
                            0 -> SceneLayersTab(
                                layers = layers,
                                selectedLayerId = selectedLayerId,
                                onSelectLayer = { selectedLayerId = it },
                                onAddLayerClick = { showAddLayerDialog = true },
                                onToggleVisibility = { id, v -> onToggleVisibility(id, v) },
                                onToggleLock = { id, l -> onToggleLock(id, l) },
                                onEditContent = { layerToEdit = it },
                                onDeleteLayer = { id ->
                                    onDeleteLayer(id)
                                    if (selectedLayerId == id) selectedLayerId = null
                                }
                            )
                            1 -> ScenePropertiesTab(
                                layer = layers.find { it.id == selectedLayerId },
                                onUpdateAlpha = { a -> selectedLayerId?.let { onUpdateLayerAlpha(it, a) } },
                                onToggleLock = {
                                    selectedLayerId?.let { id ->
                                        val layer = layers.find { it.id == id }
                                        if (layer != null) onToggleLock(id, layer.isLocked)
                                    }
                                },
                                onEditContent = {
                                    layers.find { it.id == selectedLayerId }?.let { layerToEdit = it }
                                },
                                onCenter = { selectedLayerId?.let { onCenterLayer(it) } },
                                onFitScreen = { selectedLayerId?.let { onFitToScreen(it) } },
                                onDuplicate = {
                                    layers.find { it.id == selectedLayerId }?.let { onDuplicateLayer(it) }
                                },
                                onDelete = {
                                    selectedLayerId?.let {
                                        onDeleteLayer(it)
                                        selectedLayerId = null
                                    }
                                }
                            )
                            2 -> SceneTemplatesTab(
                                onApplyTemplate = { template ->
                                    onApplyTemplate(template)
                                    selectedTab = 0
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddLayerDialog) {
        AddLayerDialog(
            onDismiss = { showAddLayerDialog = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                onAddLayer(type, name, content, width, height, alpha)
                showAddLayerDialog = false
            }
        )
    }

    layerToEdit?.let { layer ->
        EditLayerDialog(
            layer = layer,
            onDismiss = { layerToEdit = null },
            onSave = { newName, newContent ->
                onUpdateLayerContent(layer.id, newName, newContent)
                layerToEdit = null
            }
        )
    }
}

/**
 * Live Game Viewport Backdrop for 16:9 Stream Canvas (Supports Live Screen Mirroring)
 */
@Composable
private fun GameStreamViewportBackdrop(
    previewBitmap: Bitmap?,
    isPreviewActive: Boolean,
    isLivePreview: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F15)),
        contentAlignment = Alignment.Center
    ) {
        if (isPreviewActive && previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Live Game Stream Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * High-Performance, Zero-Jank Draggable & Scalable Layer on 16:9 Canvas (100% full-canvas movement)
 */
@Composable
private fun SmoothCanvasLayerItem(
    layer: SceneLayerEntity,
    isSelected: Boolean,
    isLivePreview: Boolean,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    onSelect: () -> Unit,
    onBoundsChanged: (posX: Float, posY: Float, width: Float, height: Float, scale: Float) -> Unit
) {
    val offsetX = remember(layer.id) { mutableFloatStateOf(layer.posX * canvasWidthDp) }
    val offsetY = remember(layer.id) { mutableFloatStateOf(layer.posY * canvasHeightDp) }
    val widthDp = remember(layer.id) { mutableFloatStateOf(layer.width * canvasWidthDp) }
    val heightDp = remember(layer.id) { mutableFloatStateOf(layer.height * canvasHeightDp) }

    LaunchedEffect(layer.posX, layer.posY, layer.width, layer.height, canvasWidthDp, canvasHeightDp) {
        offsetX.floatValue = layer.posX * canvasWidthDp
        offsetY.floatValue = layer.posY * canvasHeightDp
        widthDp.floatValue = layer.width * canvasWidthDp
        heightDp.floatValue = layer.height * canvasHeightDp
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    offsetX.floatValue.roundToInt(),
                    offsetY.floatValue.roundToInt()
                )
            }
            .width(widthDp.floatValue.dp)
            .height(heightDp.floatValue.dp)
            .alpha(layer.alpha)
            .then(
                if (!isLivePreview) {
                    Modifier
                        .border(
                            width = if (isSelected) 2.dp else 0.8.dp,
                            color = if (isSelected) IosBlue else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (isSelected) IosBlue.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                } else Modifier
            )
            .clickable(enabled = !isLivePreview, onClick = onSelect)
            .then(
                if (!layer.isLocked && !isLivePreview) {
                    Modifier.pointerInput(layer.id, canvasWidthDp, canvasHeightDp) {
                        detectDragGestures(
                            onDragStart = {
                                if (!isSelected) onSelect()
                            },
                            onDragEnd = {
                                val normX = (offsetX.floatValue / canvasWidthDp).coerceIn(0f, 0.98f)
                                val normY = (offsetY.floatValue / canvasHeightDp).coerceIn(0f, 0.98f)
                                val normW = (widthDp.floatValue / canvasWidthDp).coerceIn(0.03f, 1.0f)
                                val normH = (heightDp.floatValue / canvasHeightDp).coerceIn(0.02f, 1.0f)
                                onBoundsChanged(normX, normY, normW, normH, layer.scale)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val maxW = (canvasWidthDp - 20f).coerceAtLeast(0f)
                            val maxH = (canvasHeightDp - 15f).coerceAtLeast(0f)
                            offsetX.floatValue = (offsetX.floatValue + dragAmount.x).coerceIn(0f, maxW)
                            offsetY.floatValue = (offsetY.floatValue + dragAmount.y).coerceIn(0f, maxH)
                        }
                    }
                } else Modifier
            )
    ) {
        // Content rendering (Live Real WebView & Widget Previews)
        when (layer.type) {
            LayerType.WEB -> {
                WebWidgetLiveRenderer(layer = layer)
            }
            LayerType.TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = layer.content,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
            LayerType.IMAGE -> {
                if (layer.content.isNotBlank()) {
                    val imgFile = File(layer.content)
                    AsyncImage(
                        model = if (imgFile.exists()) imgFile else layer.content,
                        contentDescription = layer.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🖼️ ${layer.name}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            LayerType.CAMERA_PIP -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2E))
                        .border(1.dp, IosBlue, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("FaceCam", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }

        // Invisible touch interceptor over content in editing mode so WebView doesn't eat drags
        if (!isLivePreview && !layer.isLocked) {
            Box(modifier = Modifier.fillMaxSize())
        }

        // Selection Badge & Resize Handle
        if (isSelected && !isLivePreview) {
            Surface(
                color = IosBlue,
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            if (!layer.isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .offset(x = 8.dp, y = 8.dp)
                        .clip(CircleShape)
                        .background(IosBlue)
                        .pointerInput(layer.id, canvasWidthDp, canvasHeightDp) {
                            detectDragGestures(
                                onDragEnd = {
                                    val currentW = widthDp.floatValue
                                    val currentH = heightDp.floatValue
                                    val normX = (offsetX.floatValue / canvasWidthDp).coerceIn(0f, 1f - (currentW / canvasWidthDp).coerceIn(0.01f, 1f))
                                    val normY = (offsetY.floatValue / canvasHeightDp).coerceIn(0f, 1f - (currentH / canvasHeightDp).coerceIn(0.01f, 1f))
                                    val normW = (currentW / canvasWidthDp).coerceIn(0.03f, 1.0f)
                                    val normH = (currentH / canvasHeightDp).coerceIn(0.02f, 1.0f)
                                    onBoundsChanged(normX, normY, normW, normH, layer.scale)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val maxAllowedW = (canvasWidthDp - offsetX.floatValue).coerceAtLeast(30f)
                                val maxAllowedH = (canvasHeightDp - offsetY.floatValue).coerceAtLeast(20f)
                                widthDp.floatValue = (widthDp.floatValue + dragAmount.x).coerceIn(30f, maxAllowedW)
                                heightDp.floatValue = (heightDp.floatValue + dragAmount.y).coerceIn(20f, maxAllowedH)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Resize Handle",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tab 1: List of Layers with Reordering & Quick Visibility
 */
@Composable
private fun SceneLayersTab(
    layers: List<SceneLayerEntity>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onAddLayerClick: () -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit,
    onToggleLock: (String, Boolean) -> Unit,
    onEditContent: (SceneLayerEntity) -> Unit,
    onDeleteLayer: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "СЛОИ НА ЭКРАНЕ (${layers.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosLabelSecondary
            )

            IosGlassCapsule(
                modifier = Modifier.clickable(onClick = onAddLayerClick),
                backgroundColor = IosBlue.copy(alpha = 0.15f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = IosBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Добавить", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (layers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("На холсте пока нет виджетов", style = MaterialTheme.typography.bodyMedium, color = IosLabelSecondary)
                    Spacer(Modifier.height(8.dp))
                    IosButton(
                        text = "Добавить первый виджет",
                        icon = Icons.Default.Add,
                        onClick = onAddLayerClick,
                        height = 40.dp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(layers, key = { it.id }) { layer ->
                    val isSelected = layer.id == selectedLayerId

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) IosBlue.copy(alpha = 0.15f) else IosCardElevated,
                        border = if (isSelected) BorderStroke(1.dp, IosBlue) else BorderStroke(0.5.dp, IosGlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayer(layer.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Layer Type Icon
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (layer.type) {
                                            LayerType.WEB -> IosBlue.copy(alpha = 0.2f)
                                            LayerType.TEXT -> IosPurple.copy(alpha = 0.2f)
                                            LayerType.IMAGE -> IosPink.copy(alpha = 0.2f)
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
                                        LayerType.TEXT -> IosPurple
                                        LayerType.IMAGE -> IosPink
                                        LayerType.CAMERA_PIP -> IosOrange
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = layer.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (isSelected) IosBlue else IosLabelPrimary
                                )
                                Text(
                                    text = "${layer.type.name} • Прозрачность ${(layer.alpha * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosLabelSecondary
                                )
                            }

                            // Quick Edit Content
                            IconButton(
                                onClick = { onEditContent(layer) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = IosLabelSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Visibility Toggle
                            IconButton(
                                onClick = { onToggleVisibility(layer.id, layer.isVisible) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = if (layer.isVisible) IosLabelPrimary else IosLabelTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Lock Toggle
                            IconButton(
                                onClick = { onToggleLock(layer.id, layer.isLocked) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    tint = if (layer.isLocked) IosOrange else IosLabelTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Direct Delete Layer Button
                            IconButton(
                                onClick = { onDeleteLayer(layer.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = IosRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Selected Layer Properties & Fine Alignment
 */
@Composable
private fun ScenePropertiesTab(
    layer: SceneLayerEntity?,
    onUpdateAlpha: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onEditContent: () -> Unit,
    onCenter: () -> Unit,
    onFitScreen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    if (layer == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Выберите слой на холсте или во вкладке «Слои»",
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabelSecondary
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Layer Name & Edit Content Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = IosLabelPrimary
                )
                Text(
                    text = "Тип: ${layer.type.name} • X: ${(layer.posX * 100).toInt()}% Y: ${(layer.posY * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosLabelSecondary
                )
            }

            IosButton(
                text = "Изменить текст/URL",
                icon = Icons.Default.Edit,
                onClick = onEditContent,
                height = 36.dp
            )
        }

        HorizontalDivider(color = IosGlassBorder)

        // Alpha Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Прозрачность слоя", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
            Text("${(layer.alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
        }
        IosControlSlider(
            value = layer.alpha,
            onValueChange = onUpdateAlpha,
            valueRange = 0.05f..1.0f,
            activeColor = IosBlue
        )

        // Transform Buttons: Center, Fit Screen, Duplicate, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onCenter,
                shape = RoundedCornerShape(10.dp),
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = IosLabelPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("По центру", style = MaterialTheme.typography.labelSmall, color = IosLabelPrimary)
                }
            }

            Surface(
                onClick = onFitScreen,
                shape = RoundedCornerShape(10.dp),
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = IosLabelPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("На весь экран", style = MaterialTheme.typography.labelSmall, color = IosLabelPrimary)
                }
            }

            Surface(
                onClick = onDuplicate,
                shape = RoundedCornerShape(10.dp),
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = IosLabelPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Копия", style = MaterialTheme.typography.labelSmall, color = IosLabelPrimary)
                }
            }

            Surface(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp),
                color = IosRed.copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, IosRed.copy(alpha = 0.3f)),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = IosRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Удалить", style = MaterialTheme.typography.labelSmall, color = IosRed)
                }
            }
        }
    }
}

/**
 * Tab 3: Ready-to-Use Streaming Layout Templates
 */
@Composable
private fun SceneTemplatesTab(
    onApplyTemplate: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ГОТОВЫЕ ШАБЛОНЫ СЦЕНЫ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = IosLabelSecondary
        )

        val templates = listOf(
            Triple("CYBER_GAMER", "🎮 Cyber Gamer Pro", "Goal Bar сбора, FaceCam PiP, Twitch Чат и Водяной знак"),
            Triple("CLEAN_ESPORTS", "🏆 Киберспорт Минимал", "Минималистичный сбор донатов и верхний бейдж"),
            Triple("JUST_CHATTING", "🎙️ Общение и Подкаст", "Большой чат и крупная веб-камера"),
            Triple("GOAL_BAR_FOCUS", "🎯 Сбор Донатов (Goal Bar)", "Выделенный прогресс-бар цели и алерты DonationAlerts")
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(templates) { (key, title, desc) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IosCardElevated,
                    border = BorderStroke(0.5.dp, IosGlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onApplyTemplate(key) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = IosBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
