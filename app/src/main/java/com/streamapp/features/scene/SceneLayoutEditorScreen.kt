package com.streamapp.features.scene

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.streamapp.core.broadcaster.screen.ScreenCaptureService
import com.streamapp.core.broadcaster.screen.ScreenPreviewManager
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.components.IosSegmentedControl
import com.streamapp.core.designsystem.theme.IosBackground
import com.streamapp.features.scene.components.SceneEditorCanvasPreview
import com.streamapp.features.scene.components.SceneEditorTopBar
import com.streamapp.features.scene.components.SceneLayersTab
import com.streamapp.features.scene.components.ScenePropertiesTab
import com.streamapp.features.scene.components.SceneTemplatesTab

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
    var selectedTab by remember { mutableStateOf("Слои") } // "Слои", "Свойства", "Шаблоны"
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
            SceneEditorTopBar(
                scene = scene,
                isLivePreviewMode = isLivePreviewMode,
                isPreviewActive = isPreviewActive,
                isLandscapeCanvas = isLandscapeCanvas,
                onClose = onClose,
                onTogglePreviewActive = {
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
                onToggleLivePreviewMode = { isLivePreviewMode = !isLivePreviewMode },
                onToggleOrientation = { isLandscapeCanvas = !isLandscapeCanvas },
                onAddLayerClick = { showAddLayerDialog = true }
            )
        },
        containerColor = IosBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(IosBackground)
        ) {
            // 1. Interactive Preview Canvas
            SceneEditorCanvasPreview(
                isLandscapeCanvas = isLandscapeCanvas,
                isLivePreviewMode = isLivePreviewMode,
                isPreviewActive = isPreviewActive,
                previewBitmap = previewBitmap,
                layers = layers,
                onUpdateLayerBounds = onUpdateLayerBounds,
                onUpdateLayerAlpha = onUpdateLayerAlpha,
                onToggleLock = onToggleLock,
                onCenterLayer = onCenterLayer,
                onFitToScreen = onFitToScreen,
                onDuplicateLayer = onDuplicateLayer,
                onDeleteLayer = onDeleteLayer
            )

            Spacer(Modifier.height(8.dp))

            // 2. Segmented Tabs: Слои / Свойства / Шаблоны
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                IosSegmentedControl(
                    items = listOf("Слои", "Свойства", "Шаблоны"),
                    selectedItem = selectedTab,
                    onItemSelected = { selectedTab = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 3. Tab Contents
            when (selectedTab) {
                "Слои" -> {
                    SceneLayersTab(
                        layers = layers,
                        selectedLayerId = selectedLayerId,
                        onSelectLayer = { selectedLayerId = it },
                        onToggleVisibility = onToggleVisibility,
                        onToggleLock = onToggleLock,
                        onEditLayer = { layerToEdit = it },
                        onDuplicateLayer = onDuplicateLayer,
                        onDeleteLayer = onDeleteLayer,
                        onAddLayerClick = { showAddLayerDialog = true }
                    )
                }
                "Свойства" -> {
                    val selectedLayer = layers.find { it.id == selectedLayerId }
                    ScenePropertiesTab(
                        selectedLayer = selectedLayer,
                        onUpdateBounds = { x, y, w, h, scale ->
                            selectedLayer?.let { onUpdateLayerBounds(it.id, x, y, w, h, scale) }
                        },
                        onUpdateAlpha = { alpha ->
                            selectedLayer?.let { onUpdateLayerAlpha(it.id, alpha) }
                        },
                        onCenterLayer = { selectedLayer?.let { onCenterLayer(it.id) } },
                        onFitToScreen = { selectedLayer?.let { onFitToScreen(it.id) } },
                        onDuplicateLayer = { selectedLayer?.let { onDuplicateLayer(it) } },
                        onDeleteLayer = { selectedLayer?.let { onDeleteLayer(it.id) } }
                    )
                }
                "Шаблоны" -> {
                    SceneTemplatesTab(
                        onApplyTemplate = onApplyTemplate
                    )
                }
            }
        }
    }

    // Add Layer Dialog
    if (showAddLayerDialog) {
        AddLayerDialog(
            onDismiss = { showAddLayerDialog = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                onAddLayer(type, name, content, width, height, alpha)
                showAddLayerDialog = false
            }
        )
    }

    // Edit Layer Dialog
    layerToEdit?.let { layer ->
        EditLayerDialog(
            layer = layer,
            onDismiss = { layerToEdit = null },
            onSave = { name, content ->
                onUpdateLayerContent(layer.id, name, content)
                layerToEdit = null
            }
        )
    }
}
