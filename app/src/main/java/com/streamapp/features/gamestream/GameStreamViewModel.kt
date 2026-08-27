package com.streamapp.features.gamestream

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.broadcaster.overlay.OverlayEngine
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.features.StreamHealthMonitor
import com.streamapp.core.features.StreamHealthStats
import com.streamapp.core.model.InstalledAppInfo
import com.streamapp.core.model.ScreenCaptureMode
import com.streamapp.core.broadcaster.screen.ScreenPreviewManager
import com.streamapp.features.chat.ChatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameStreamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val destinationDao: DestinationDao,
    val overlayEngine: OverlayEngine,
    val settingsRepository: SettingsRepository,
    val chatManager: ChatManager,
    val streamHealthMonitor: StreamHealthMonitor,
    val screenPreviewManager: ScreenPreviewManager
) : ViewModel() {
    private val _resolution = MutableStateFlow("1080p")
    val resolution: StateFlow<String> = _resolution.asStateFlow()

    private val _bitrate = MutableStateFlow(6000)
    val bitrate: StateFlow<Int> = _bitrate.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

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
        loadInstalledApps()
        loadPersistedSettings()
    }

    private fun loadPersistedSettings() {
        viewModelScope.launch {
            val s = settingsRepository.settings.firstOrNull() ?: return@launch
            _resolution.value = s.videoResolution
            _bitrate.value = s.videoBitrate
            _fps.value = s.videoFps
            _captureMode.value = if (s.captureModeOrdinal == 1) ScreenCaptureMode.SPECIFIC_APP else ScreenCaptureMode.ENTIRE_SCREEN
            
            if (s.selectedAppPackage.isNotBlank()) {
                val app = _installedApps.value.find { it.packageName == s.selectedAppPackage }
                if (app != null) {
                    _selectedApp.value = app
                }
            }
        }
    }

    private fun persistSettings() {
        viewModelScope.launch {
            val current = settingsRepository.settings.firstOrNull() ?: return@launch
            settingsRepository.updateSettings(
                current.copy(
                    videoResolution = _resolution.value,
                    videoBitrate = _bitrate.value,
                    videoFps = _fps.value,
                    captureModeOrdinal = if (_captureMode.value == ScreenCaptureMode.SPECIFIC_APP) 1 else 0,
                    selectedAppPackage = _selectedApp.value?.packageName ?: ""
                )
            )
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = pm.queryIntentActivities(intent, 0)
                val apps = activities
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { resolveInfo ->
                        InstalledAppInfo(
                            name = resolveInfo.loadLabel(pm).toString(),
                            packageName = resolveInfo.activityInfo.packageName,
                            icon = resolveInfo.loadIcon(pm)
                        )
                    }
                    .sortedBy { it.name }
                _installedApps.value = apps

                // Restore selected app if packageName was persisted
                val s = settingsRepository.settings.firstOrNull()
                if (s != null && s.selectedAppPackage.isNotBlank()) {
                    val match = apps.find { it.packageName == s.selectedAppPackage }
                    if (match != null) {
                        _selectedApp.value = match
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }
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
        _resolution.value = res
        persistSettings()
    }

    fun updateBitrate(bitrate: Int) {
        _bitrate.value = bitrate
        persistSettings()
    }

    fun updateFps(fps: Int) {
        _fps.value = fps
        persistSettings()
    }

    fun setStreamingState(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
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
