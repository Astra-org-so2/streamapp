package com.streamapp.features.gamestream

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.broadcaster.overlay.OverlayEngine
import com.streamapp.core.broadcaster.screen.ScreenPreviewManager
import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.broadcaster.stream.BroadcastState
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.features.StreamHealthMonitor
import com.streamapp.core.features.StreamHealthStats
import com.streamapp.core.model.AppSettings
import com.streamapp.core.model.InstalledAppInfo
import com.streamapp.core.model.ScreenCaptureMode
import com.streamapp.features.chat.ChatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GameStreamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val destinationDao: DestinationDao,
    val overlayEngine: OverlayEngine,
    val settingsRepository: SettingsRepository,
    val chatManager: ChatManager,
    val streamHealthMonitor: StreamHealthMonitor,
    val screenPreviewManager: ScreenPreviewManager,
    val broadcastManager: BroadcastManager
) : ViewModel() {

    private val _resolution = MutableStateFlow("1080p")
    val resolution: StateFlow<String> = _resolution.asStateFlow()

    private val _bitrate = MutableStateFlow(6000)
    val bitrate: StateFlow<Int> = _bitrate.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Real streaming state derived directly from BroadcastManager / ScreenCaptureService
    val isStreaming: StateFlow<Boolean> = broadcastManager.broadcastState
        .map { it == BroadcastState.STREAMING || it == BroadcastState.CONNECTING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val broadcastState: StateFlow<BroadcastState> = broadcastManager.broadcastState

    val healthStats: StateFlow<StreamHealthStats> = streamHealthMonitor.healthStats

    private val _captureMode = MutableStateFlow(ScreenCaptureMode.ENTIRE_SCREEN)
    val captureMode: StateFlow<ScreenCaptureMode> = _captureMode.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _selectedApp = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedApp: StateFlow<InstalledAppInfo?> = _selectedApp.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val allScenes: StateFlow<List<SceneEntity>> = overlayEngine.allScenes
    val activeScene: StateFlow<SceneEntity?> = overlayEngine.activeScene
    val activeLayers: StateFlow<List<SceneLayerEntity>> = overlayEngine.activeLayers

    init {
        initializeSettingsAndApps()
    }

    private fun initializeSettingsAndApps() {
        viewModelScope.launch {
            // 1. Read persisted settings first
            val s = settingsRepository.settings.firstOrNull() ?: AppSettings()
            _resolution.value = s.videoResolution
            _bitrate.value = s.videoBitrate
            _fps.value = s.videoFps
            _captureMode.value = if (s.captureModeOrdinal == 1) ScreenCaptureMode.SPECIFIC_APP else ScreenCaptureMode.ENTIRE_SCREEN

            // 2. Load installed apps on IO with comprehensive error logging
            val apps = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val activities = pm.queryIntentActivities(intent, 0)
                    activities
                        .filter { it.activityInfo.packageName != context.packageName }
                        .map { resolveInfo ->
                            InstalledAppInfo(
                                name = resolveInfo.loadLabel(pm).toString(),
                                packageName = resolveInfo.activityInfo.packageName,
                                icon = resolveInfo.loadIcon(pm)
                            )
                        }
                        .sortedBy { it.name }
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.FEATURES, "Failed to load installed apps from PackageManager", e)
                    emptyList()
                }
            }
            _installedApps.value = apps

            // 3. Atomically restore selected app
            if (s.selectedAppPackage.isNotBlank()) {
                val matched = apps.find { it.packageName == s.selectedAppPackage }
                _selectedApp.value = matched
            }
        }
    }

    private fun persistSettings() {
        viewModelScope.launch {
            settingsRepository.mutateSettings { current ->
                current.copy(
                    videoResolution = _resolution.value,
                    videoBitrate = _bitrate.value,
                    videoFps = _fps.value,
                    captureModeOrdinal = if (_captureMode.value == ScreenCaptureMode.SPECIFIC_APP) 1 else 0,
                    selectedAppPackage = _selectedApp.value?.packageName ?: ""
                )
            }
        }
    }

    /**
     * Validates that at least one enabled destination exists and specific app is launchable.
     * Returns the target RTMP URL if valid, or null with error set.
     */
    suspend fun validateAndGetTargetUrl(): String? {
        val enabledDestinations = destinationDao.getEnabledDestinations().firstOrNull()
        val activeDest = enabledDestinations?.firstOrNull()
        if (activeDest == null) {
            _errorMessage.value = "Нет активных платформ для трансляции. Включите хотя бы один стриминговый сервис во вкладке «Платформы»."
            return null
        }

        if (_captureMode.value == ScreenCaptureMode.SPECIFIC_APP) {
            val app = _selectedApp.value
            if (app == null) {
                _errorMessage.value = "Выберите игру/приложение для запуска стрима"
                return null
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent == null) {
                _errorMessage.value = "Приложение «${app.name}» удалено или не может быть запущено"
                return null
            }
        }

        _errorMessage.value = null
        return "${activeDest.rtmpUrl}/${activeDest.streamKey}"
    }

    fun setCaptureMode(mode: ScreenCaptureMode) {
        _captureMode.value = mode
        persistSettings()
    }

    fun selectApp(app: InstalledAppInfo?) {
        _selectedApp.value = app
        persistSettings()
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun updateResolution(res: String) {
        if (isStreaming.value) return // Disallow resolution change during live stream
        _resolution.value = res
        persistSettings()
    }

    fun updateBitrate(bitrate: Int) {
        _bitrate.value = bitrate
        persistSettings()
        if (isStreaming.value) {
            broadcastManager.updateBitrateOnFly(bitrate)
        }
    }

    fun updateFps(fps: Int) {
        if (isStreaming.value) return // Disallow FPS change during live stream
        _fps.value = fps
        persistSettings()
    }

    fun clearError() {
        _errorMessage.value = null
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
}
