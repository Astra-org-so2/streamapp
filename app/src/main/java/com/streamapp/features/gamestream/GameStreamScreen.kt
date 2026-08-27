package com.streamapp.features.gamestream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.broadcaster.screen.ScreenCaptureService
import com.streamapp.core.broadcaster.screen.FloatingControlsService
import com.streamapp.core.designsystem.components.*
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.features.NetworkHealthLevel
import com.streamapp.core.model.InstalledAppInfo
import com.streamapp.core.model.ScreenCaptureMode
import com.streamapp.features.chat.LiveChatSheet
import com.streamapp.features.scene.AddLayerDialog
import com.streamapp.features.scene.LayersBottomSheet
import com.streamapp.features.scene.SceneLayoutEditorScreen
import com.streamapp.features.scene.SceneQuickSwitcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStreamScreen(viewModel: GameStreamViewModel = hiltViewModel()) {
    val resolution by viewModel.resolution.collectAsState()
    val bitrate by viewModel.bitrate.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val healthStats by viewModel.healthStats.collectAsState()
    val captureMode by viewModel.captureMode.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val appSearchQuery by viewModel.appSearchQuery.collectAsState()

    val scenes by viewModel.allScenes.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val layers by viewModel.activeLayers.collectAsState()

    var showLayoutStudio by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.viewModelScope.launch {
                val targetUrl = viewModel.validateAndGetTargetUrl() ?: return@launch

                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_URL, targetUrl)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                // Start in-game floating HUD if overlay permission is granted
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                    android.provider.Settings.canDrawOverlays(context)
                ) {
                    val floatingIntent = Intent(context, FloatingControlsService::class.java)
                    context.startService(floatingIntent)
                }

                if (captureMode == ScreenCaptureMode.SPECIFIC_APP && selectedApp != null) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedApp!!.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }
            }
        }
    }

    // Fullscreen Dedicated Scene Layout Studio
    if (showLayoutStudio) {
        SceneLayoutEditorScreen(
            scene = activeScene,
            layers = layers,
            screenPreviewManager = viewModel.screenPreviewManager,
            onClose = { showLayoutStudio = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                val sceneId = activeScene?.id ?: return@SceneLayoutEditorScreen
                viewModel.overlayEngine.addLayer(sceneId, type, name, content, width, height, alpha = alpha)
            },
            onUpdateLayerContent = { id, name, content ->
                viewModel.overlayEngine.updateLayerContent(id, name, content)
            },
            onApplyTemplate = { viewModel.applySceneTemplate(it) },
            onUpdateLayerBounds = { id, x, y, w, h, s ->
                viewModel.overlayEngine.updateLayerBounds(id, x, y, w, h, s)
            },
            onUpdateLayerAlpha = { id, a -> viewModel.overlayEngine.updateLayerAlpha(id, a) },
            onToggleVisibility = { id, v -> viewModel.overlayEngine.toggleLayerVisibility(id, v) },
            onToggleLock = { id, l -> viewModel.overlayEngine.toggleLayerLock(id, l) },
            onCenterLayer = { id -> viewModel.overlayEngine.centerLayer(id) },
            onFitToScreen = { id -> viewModel.overlayEngine.fitLayerToScreen(id) },
            onDuplicateLayer = { layer -> viewModel.overlayEngine.duplicateLayer(layer) },
            onDeleteLayer = { id -> viewModel.overlayEngine.deleteLayer(id) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
    ) {
        // Scene Switcher Bar
        SceneQuickSwitcher(
            scenes = scenes,
            activeScene = activeScene,
            onSelectScene = { viewModel.selectScene(it) },
            onCreateScene = { viewModel.createScene(it) },
            onDeleteScene = { viewModel.overlayEngine.deleteScene(it) },
            onOpenLayersClick = { showLayersSheet = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // iOS Header & Live Chat Quick Access
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Трансляция Экрана", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = IosLabelPrimary)
                    Text("Стриминг мобильных игр и экрана смартфона", style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary)
                }

                Surface(
                    onClick = { showChatSheet = true },
                    shape = CircleShape,
                    color = IosPurple.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, IosPurple.copy(alpha = 0.3f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Forum, contentDescription = "Live Chat", tint = IosPurple, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Error banner if any
            errorMessage?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = IosRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, IosRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = IosRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = IosRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Stream Health HUD Status (if streaming or ready)
            IosCard(
                backgroundColor = if (isStreaming) IosCardElevated else IosCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when (healthStats.healthLevel) {
                            NetworkHealthLevel.EXCELLENT, NetworkHealthLevel.GOOD -> IosGreen
                            NetworkHealthLevel.UNSTABLE -> IosYellow
                            NetworkHealthLevel.CRITICAL -> IosRed
                        }

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) dotColor else IosGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isStreaming) "В ЭФИРЕ: ${healthStats.bitrateKbps} kbps • ${healthStats.fps} FPS" else "ГОТОВ К ЭФИРУ: ${resolution} • ${fps} FPS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = IosLabelPrimary
                        )
                    }

                    Text(
                        text = if (isStreaming) "Сеть: ${healthStats.stabilityScore}%" else "Битрейт: ${bitrate}k",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStreaming) IosGreen else IosLabelSecondary
                    )
                }
            }

            // Section 1: Capture Mode Segmented Control
            IosCard {
                Text(
                    text = "РЕЖИМ ЗАХВАТА",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = IosLabelSecondary
                )
                Spacer(Modifier.height(10.dp))

                IosSegmentedControl(
                    items = listOf(ScreenCaptureMode.ENTIRE_SCREEN, ScreenCaptureMode.SPECIFIC_APP),
                    selectedItem = captureMode,
                    onItemSelected = { mode ->
                        viewModel.setCaptureMode(mode)
                        if (mode == ScreenCaptureMode.SPECIFIC_APP && selectedApp == null) {
                            showAppPickerSheet = true
                        }
                    },
                    itemLabel = {
                        when (it) {
                            ScreenCaptureMode.ENTIRE_SCREEN -> "📱 Весь экран"
                            ScreenCaptureMode.SPECIFIC_APP -> "🎮 Выбор игры"
                        }
                    }
                )

                // If Specific Game Selected
                if (captureMode == ScreenCaptureMode.SPECIFIC_APP) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IosCardElevated)
                            .clickable { showAppPickerSheet = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedApp?.icon != null) {
                            Image(
                                bitmap = selectedApp!!.icon!!.toBitmap(96, 96).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(9.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(IosPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Gamepad, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedApp?.name ?: "Нажмите для выбора игры...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosLabelPrimary
                            )
                            Text(
                                text = if (selectedApp != null) "Авто-запуск при старте" else "Выберите из установленных приложений",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosLabelSecondary
                            )
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = IosLabelTertiary)
                    }
                }
            }

            // Section 2: Visual 16:9 Studio Card
            IosCard {
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
                                .background(IosBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DesignServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Макет Сцены (16:9)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosLabelPrimary
                            )
                            Text(
                                text = "${layers.count { it.isVisible }} активных виджетов на экране",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosLabelSecondary
                            )
                        }
                    }

                    IosGlassCapsule(
                        modifier = Modifier.clickable { showLayoutStudio = true },
                        backgroundColor = IosBlue.copy(alpha = 0.15f)
                    ) {
                        Text("Редактор", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                    }
                }

                Spacer(Modifier.height(12.dp))

                IosButton(
                    text = "Открыть Студию Макета",
                    icon = Icons.Default.OpenInFull,
                    brush = IosBlueGradient,
                    onClick = { showLayoutStudio = true },
                    height = 46.dp
                )
            }

            // Section 3: Video Quality Settings (Grouped Inset Card)
            IosCard {
                Text(
                    text = "ПАРАМЕТРЫ КАЧЕСТВА ПОТОКА",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = IosLabelSecondary
                )
                Spacer(Modifier.height(10.dp))

                // Resolution Segment
                Text(
                    text = if (isStreaming) "Разрешение (зафиксировано в эфире)" else "Разрешение",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStreaming) IosLabelTertiary else IosLabelSecondary
                )
                Spacer(Modifier.height(6.dp))
                IosSegmentedControl(
                    items = listOf("720p", "1080p", "1440p"),
                    selectedItem = resolution,
                    onItemSelected = { if (!isStreaming) viewModel.updateResolution(it) }
                )

                Spacer(Modifier.height(14.dp))

                // FPS Segment
                Text(
                    text = if (isStreaming) "Частота кадров (зафиксировано в эфире)" else "Частота кадров (FPS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStreaming) IosLabelTertiary else IosLabelSecondary
                )
                Spacer(Modifier.height(6.dp))
                IosSegmentedControl(
                    items = listOf(30, 60),
                    selectedItem = fps,
                    onItemSelected = { if (!isStreaming) viewModel.updateFps(it) },
                    itemLabel = { "$it FPS" }
                )

                Spacer(Modifier.height(14.dp))

                // Bitrate Slider (Dynamic runtime bitrate adjustment)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStreaming) "Битрейт видео (на лету)" else "Битрейт видео",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosLabelSecondary
                    )
                    Text("${bitrate} kbps", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                }
                Spacer(Modifier.height(6.dp))
                IosControlSlider(
                    value = bitrate.toFloat(),
                    onValueChange = { viewModel.updateBitrate(it.toInt()) },
                    valueRange = 1000f..15000f,
                    activeColor = IosBlue
                )
            }

            Spacer(Modifier.height(6.dp))

            // Main Broadcast Action Button
            IosButton(
                text = if (isStreaming) "ОСТАНОВИТЬ СТРИМ" else if (captureMode == ScreenCaptureMode.SPECIFIC_APP && selectedApp != null) "ЗАПУСТИТЬ: ${selectedApp!!.name}" else "НАЧАТЬ ТРАНСЛЯЦИЮ",
                icon = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                brush = if (isStreaming) IosLiveRedGradient else IosBlueGradient,
                onClick = {
                    if (isStreaming) {
                        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    } else {
                        viewModel.viewModelScope.launch {
                            val targetUrl = viewModel.validateAndGetTargetUrl() ?: return@launch
                            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val captureIntent = if (android.os.Build.VERSION.SDK_INT >= 34) {
                                val config = android.media.projection.MediaProjectionConfig.createConfigForUserChoice()
                                mediaProjectionManager.createScreenCaptureIntent(config)
                            } else {
                                mediaProjectionManager.createScreenCaptureIntent()
                            }
                            screenCaptureLauncher.launch(captureIntent)
                        }
                    }
                },
                height = 54.dp
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // App Picker Bottom Sheet
    if (showAppPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppPickerSheet = false },
            containerColor = IosCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(IosLabelTertiary)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Выберите приложение для стрима",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = IosLabelPrimary
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { viewModel.setAppSearchQuery(it) },
                    placeholder = { Text("Поиск игр и приложений...", color = IosLabelSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IosLabelSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosGlassBorder,
                        focusedContainerColor = IosCardElevated,
                        unfocusedContainerColor = IosCardElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                val filteredApps = installedApps.filter {
                    it.name.contains(appSearchQuery, ignoreCase = true) || it.packageName.contains(appSearchQuery, ignoreCase = true)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName == selectedApp?.packageName
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) IosBlue.copy(alpha = 0.15f) else IosCardElevated,
                            border = if (isSelected) BorderStroke(1.dp, IosBlue) else BorderStroke(0.5.dp, IosGlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectApp(app)
                                    showAppPickerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon!!.toBitmap(96, 96).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (isSelected) IosBlue else IosLabelPrimary)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = IosLabelSecondary, maxLines = 1)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Layers Bottom Sheet
    if (showLayersSheet) {
        LayersBottomSheet(
            sceneName = activeScene?.name ?: "Scene",
            layers = layers,
            onDismiss = { showLayersSheet = false },
            onAddLayerClick = {
                showLayersSheet = false
                showAddLayerDialog = true
            },
            onToggleVisibility = { id, vis -> viewModel.overlayEngine.toggleLayerVisibility(id, vis) },
            onDeleteLayer = { id -> viewModel.overlayEngine.deleteLayer(id) }
        )
    }

    // Add Layer Dialog
    if (showAddLayerDialog) {
        AddLayerDialog(
            onDismiss = { showAddLayerDialog = false },
            onAddLayer = { type, name, content, width, height, alpha ->
                val sceneId = activeScene?.id ?: return@AddLayerDialog
                viewModel.overlayEngine.addLayer(sceneId, type, name, content, width, height, alpha = alpha)
                showAddLayerDialog = false
            }
        )
    }

    // Live Chat Sheet
    if (showChatSheet) {
        LiveChatSheet(
            chatManager = viewModel.chatManager,
            onDismiss = { showChatSheet = false }
        )
    }
}
