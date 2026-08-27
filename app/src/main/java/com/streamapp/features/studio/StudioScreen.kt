package com.streamapp.features.studio

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.streamapp.core.broadcaster.camera.CameraController
import com.streamapp.core.broadcaster.overlay.OverlayEngine
import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.broadcaster.screen.ScreenPreviewManager
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.components.IosGlassCapsule
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.features.NetworkHealthLevel
import com.streamapp.core.features.StreamHealthMonitor
import com.streamapp.core.features.StreamHealthStats
import com.streamapp.features.chat.ChatManager
import com.streamapp.features.chat.LiveChatSheet
import com.streamapp.features.scene.AddLayerDialog
import com.streamapp.features.scene.LayersBottomSheet
import com.streamapp.features.scene.SceneLayerCanvas
import com.streamapp.features.scene.SceneLayoutEditorScreen
import com.streamapp.features.scene.SceneQuickSwitcher
import com.streamapp.core.broadcaster.stream.BroadcastState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudioViewModel @Inject constructor(
    val broadcastManager: BroadcastManager,
    val cameraController: CameraController,
    val overlayEngine: OverlayEngine,
    val destinationDao: DestinationDao,
    val chatManager: ChatManager,
    val streamHealthMonitor: StreamHealthMonitor,
    val screenPreviewManager: ScreenPreviewManager
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isLive: StateFlow<Boolean> = broadcastManager.broadcastState
        .map { it == BroadcastState.STREAMING || it == BroadcastState.CONNECTING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val healthStats: StateFlow<StreamHealthStats> = streamHealthMonitor.healthStats

    val allScenes: StateFlow<List<SceneEntity>> = overlayEngine.allScenes
    val activeScene: StateFlow<SceneEntity?> = overlayEngine.activeScene
    val activeLayers: StateFlow<List<SceneLayerEntity>> = overlayEngine.activeLayers

    fun toggleLive() {
        viewModelScope.launch {
            if (isLive.value) {
                broadcastManager.stopStreaming()
            } else {
                val activeDest = destinationDao.getEnabledDestinations().firstOrNull()?.firstOrNull()
                if (activeDest == null) {
                    _errorMessage.value = "Нет активных платформ для стрима. Включите платформу во вкладке «Платформы»."
                    return@launch
                }
                _errorMessage.value = null
                val rtmpUrl = "${activeDest.rtmpUrl}/${activeDest.streamKey}"
                broadcastManager.startStreaming(rtmpUrl)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleTorch() {
        cameraController.toggleTorch()
    }

    fun switchCamera() {
        broadcastManager.switchCamera()
    }

    fun selectScene(sceneId: String) {
        overlayEngine.selectScene(sceneId)
    }

    fun createScene(name: String) {
        overlayEngine.createScene(name)
    }

    fun applySceneTemplate(templateKey: String) {
        val sceneId = activeScene.value?.id ?: return
        overlayEngine.applySceneTemplate(sceneId, templateKey)
    }

    fun addLayer(
        type: LayerType,
        name: String,
        content: String,
        width: Float = 0.8f,
        height: Float = 0.25f,
        alpha: Float = 1.0f
    ) {
        val sceneId = activeScene.value?.id ?: return
        overlayEngine.addLayer(
            sceneId = sceneId,
            type = type,
            name = name,
            content = content,
            width = width,
            height = height,
            alpha = alpha
        )
    }

    fun updateLayerContent(layerId: String, name: String, content: String) {
        overlayEngine.updateLayerContent(layerId, name, content)
    }

    fun updateLayerBounds(
        layerId: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        scale: Float
    ) {
        overlayEngine.updateLayerBounds(layerId, posX, posY, width, height, scale)
    }

    fun updateLayerAlpha(layerId: String, alpha: Float) {
        overlayEngine.updateLayerAlpha(layerId, alpha)
    }

    fun toggleLayerVisibility(layerId: String, currentVisible: Boolean) {
        overlayEngine.toggleLayerVisibility(layerId, currentVisible)
    }

    fun toggleLayerLock(layerId: String, currentLocked: Boolean) {
        overlayEngine.toggleLayerLock(layerId, currentLocked)
    }

    fun centerLayer(layerId: String) {
        overlayEngine.centerLayer(layerId)
    }

    fun fitLayerToScreen(layerId: String) {
        overlayEngine.fitLayerToScreen(layerId)
    }

    fun duplicateLayer(layer: SceneLayerEntity) {
        overlayEngine.duplicateLayer(layer)
    }

    fun deleteLayer(layerId: String) {
        overlayEngine.deleteLayer(layerId)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StudioScreen(viewModel: StudioViewModel = hiltViewModel()) {
    val isLive by viewModel.isLive.collectAsState()
    val healthStats by viewModel.healthStats.collectAsState()
    val torchEnabled by viewModel.cameraController.isTorchOn.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val scenes by viewModel.allScenes.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val layers by viewModel.activeLayers.collectAsState()

    var showLayoutStudio by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    if (!permissionsState.allPermissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize().background(IosBackground),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Для трансляции необходимы разрешения на Камеру и Микрофон.", color = IosLabelPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Предоставить доступ", color = Color.White)
            }
        }
        return
    }

    // Dedicated Fullscreen Scene Layout Studio
    if (showLayoutStudio) {
        SceneLayoutEditorScreen(
            scene = activeScene,
            layers = layers,
            screenPreviewManager = viewModel.screenPreviewManager,
            onClose = { showLayoutStudio = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                viewModel.addLayer(type, name, content, width, height, alpha)
            },
            onUpdateLayerContent = { id, name, content ->
                viewModel.updateLayerContent(id, name, content)
            },
            onApplyTemplate = { viewModel.applySceneTemplate(it) },
            onUpdateLayerBounds = { id, x, y, w, h, s ->
                viewModel.updateLayerBounds(id, x, y, w, h, s)
            },
            onUpdateLayerAlpha = { id, a -> viewModel.updateLayerAlpha(id, a) },
            onToggleVisibility = { id, v -> viewModel.toggleLayerVisibility(id, v) },
            onToggleLock = { id, l -> viewModel.toggleLayerLock(id, l) },
            onCenterLayer = { id -> viewModel.centerLayer(id) },
            onFitToScreen = { id -> viewModel.fitLayerToScreen(id) },
            onDuplicateLayer = { layer -> viewModel.duplicateLayer(layer) },
            onDeleteLayer = { id -> viewModel.deleteLayer(id) }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(IosBackground)) {
        // Camera Viewfinder (OpenGL)
        AndroidView(
            factory = { ctx ->
                com.pedro.library.view.OpenGlView(ctx).apply {
                    viewModel.broadcastManager.initCameraStreaming(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Interactive Layer Canvas (Draggable, Scalable, Opacity)
        SceneLayerCanvas(
            layers = layers,
            isEditMode = isEditMode,
            onUpdateLayerBounds = { id, x, y, w, h, scale ->
                viewModel.updateLayerBounds(id, x, y, w, h, scale)
            },
            onUpdateLayerAlpha = { id, alpha -> viewModel.updateLayerAlpha(id, alpha) },
            onToggleLock = { id, lock -> viewModel.toggleLayerLock(id, lock) },
            onCenterLayer = { id -> viewModel.centerLayer(id) },
            onFitToScreen = { id -> viewModel.fitLayerToScreen(id) },
            onDuplicateLayer = { layer -> viewModel.duplicateLayer(layer) },
            onDeleteLayer = { id -> viewModel.deleteLayer(id) },
            modifier = Modifier.fillMaxSize()
        )

        // Top Scene Bar & Mode Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            // Quick Scene Switcher
            SceneQuickSwitcher(
                scenes = scenes,
                activeScene = activeScene,
                onSelectScene = { viewModel.selectScene(it) },
                onCreateScene = { viewModel.createScene(it) },
                onDeleteScene = { viewModel.overlayEngine.deleteScene(it) },
                onOpenLayersClick = { showLayersSheet = true }
            )

            // Dynamic Island Capsule HUD (with live health status)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosGlassCapsule {
                    val healthDotColor = when (healthStats.healthLevel) {
                        NetworkHealthLevel.EXCELLENT -> IosGreen
                        NetworkHealthLevel.GOOD -> IosGreen
                        NetworkHealthLevel.UNSTABLE -> IosYellow
                        NetworkHealthLevel.CRITICAL -> IosRed
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLive) healthDotColor else IosGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isLive) "LIVE ${healthStats.bitrateKbps}k • ${healthStats.fps}fps (${healthStats.stabilityScore}%)" else "PREVIEW ${healthStats.fps}fps",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = IosLabelPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Open Live Chat Sheet Button
                    Surface(
                        onClick = { showChatSheet = true },
                        shape = CircleShape,
                        color = IosCard.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, IosGlassBorder),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Forum, contentDescription = "Live Chat", tint = IosPurple, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Open 16:9 Canvas Studio Button
                    Surface(
                        onClick = { showLayoutStudio = true },
                        shape = CircleShape,
                        color = IosCard.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, IosGlassBorder),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DesignServices, contentDescription = null, tint = IosBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Макет", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                        }
                    }

                    // Quick Edit Toggle
                    Surface(
                        onClick = { isEditMode = !isEditMode },
                        shape = CircleShape,
                        color = if (isEditMode) IosOrange else IosCard.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, IosGlassBorder),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isEditMode) Color.Black else IosLabelPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Error banner if any
            errorMessage?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = IosRed.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Bottom Controls: Apple Style Shutter / Go Live Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Torch Glass Button
            Surface(
                onClick = { viewModel.toggleTorch() },
                shape = CircleShape,
                color = if (torchEnabled) IosYellow else IosCard.copy(alpha = 0.85f),
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (torchEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Apple Pro Live Button (Concentric Rings)
            val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isLive) 1.15f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { viewModel.toggleLive() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(if (isLive) IosLiveRedGradient else IosBlueGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = "Go Live",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Flip Camera Glass Button
            Surface(
                onClick = { viewModel.switchCamera() },
                shape = CircleShape,
                color = IosCard.copy(alpha = 0.85f),
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Bottom Sheet for Layer Management
    if (showLayersSheet) {
        LayersBottomSheet(
            sceneName = activeScene?.name ?: "Scene",
            layers = layers,
            onDismiss = { showLayersSheet = false },
            onAddLayerClick = {
                showLayersSheet = false
                showAddLayerDialog = true
            },
            onToggleVisibility = { id, vis -> viewModel.toggleLayerVisibility(id, vis) },
            onDeleteLayer = { id -> viewModel.deleteLayer(id) }
        )
    }

    // Dialog for Adding Layer / Preset
    if (showAddLayerDialog) {
        AddLayerDialog(
            onDismiss = { showAddLayerDialog = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                viewModel.addLayer(type, name, content, width, height, alpha)
                showAddLayerDialog = false
            }
        )
    }

    // Live Chat Sheet Modal
    if (showChatSheet) {
        LiveChatSheet(
            chatManager = viewModel.chatManager,
            onDismiss = { showChatSheet = false }
        )
    }
}
