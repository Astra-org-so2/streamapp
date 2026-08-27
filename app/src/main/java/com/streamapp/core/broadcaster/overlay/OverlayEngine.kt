package com.streamapp.core.broadcaster.overlay

import com.streamapp.core.database.dao.SceneDao
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayEngine @Inject constructor(
    private val sceneDao: SceneDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allScenes: StateFlow<List<SceneEntity>> = sceneDao.getAllScenes()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeScene: StateFlow<SceneEntity?> = sceneDao.getActiveScene()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _activeLayers = MutableStateFlow<List<SceneLayerEntity>>(emptyList())
    val activeLayers: StateFlow<List<SceneLayerEntity>> = _activeLayers.asStateFlow()

    init {
        scope.launch {
            allScenes.collect { scenes ->
                if (scenes.isEmpty()) {
                    // Seed initial default scene with DonationAlerts layer
                    val defaultScene = SceneEntity(
                        id = UUID.randomUUID().toString(),
                        name = "Main Live",
                        isSelected = true,
                        orderIndex = 0
                    )
                    sceneDao.insertScene(defaultScene)

                    val defaultAlertLayer = SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = defaultScene.id,
                        type = LayerType.WEB,
                        name = "Donation Alerts",
                        content = "https://www.donationalerts.com/widget/alerts?token=",
                        posX = 0.05f,
                        posY = 0.05f,
                        width = 0.9f,
                        height = 0.3f,
                        zIndex = 0
                    )
                    sceneDao.insertLayer(defaultAlertLayer)
                }
            }
        }

        scope.launch {
            activeScene.collect { scene ->
                if (scene != null) {
                    sceneDao.getLayersForScene(scene.id).collect { layers ->
                        _activeLayers.value = layers
                    }
                } else {
                    _activeLayers.value = emptyList()
                }
            }
        }
    }

    fun selectScene(sceneId: String) {
        scope.launch {
            sceneDao.setActiveScene(sceneId)
        }
    }

    fun createScene(name: String) {
        scope.launch {
            val count = allScenes.value.size
            val newScene = SceneEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                isSelected = true,
                orderIndex = count
            )
            sceneDao.insertScene(newScene)
            sceneDao.setActiveScene(newScene.id)
        }
    }

    fun deleteScene(sceneId: String) {
        scope.launch {
            val scenes = allScenes.value
            if (scenes.size <= 1) return@launch // Don't delete last scene

            sceneDao.deleteSceneById(sceneId)
            val remaining = scenes.filter { it.id != sceneId }
            remaining.firstOrNull()?.let {
                sceneDao.setActiveScene(it.id)
            }
        }
    }

    fun addLayer(
        sceneId: String,
        type: LayerType,
        name: String,
        content: String,
        posX: Float = 0.1f,
        posY: Float = 0.1f,
        width: Float = 0.8f,
        height: Float = 0.25f,
        scale: Float = 1.0f,
        alpha: Float = 1.0f
    ) {
        scope.launch {
            val zIndex = (activeLayers.value.maxOfOrNull { it.zIndex } ?: 0) + 1
            val layer = SceneLayerEntity(
                id = UUID.randomUUID().toString(),
                sceneId = sceneId,
                type = type,
                name = name,
                content = content,
                posX = posX,
                posY = posY,
                width = width,
                height = height,
                scale = scale,
                alpha = alpha,
                zIndex = zIndex
            )
            sceneDao.insertLayer(layer)
        }
    }

    fun updateLayerContent(layerId: String, name: String, content: String) {
        scope.launch {
            sceneDao.updateLayerContent(layerId, name, content)
        }
    }

    fun toggleLayerVisibility(layerId: String, currentVisible: Boolean) {
        scope.launch {
            sceneDao.setLayerVisibility(layerId, !currentVisible)
        }
    }

    fun toggleLayerLock(layerId: String, currentLocked: Boolean) {
        scope.launch {
            sceneDao.setLayerLock(layerId, !currentLocked)
        }
    }

    fun updateLayerAlpha(layerId: String, alpha: Float) {
        scope.launch {
            sceneDao.setLayerAlpha(layerId, alpha.coerceIn(0.05f, 1.0f))
        }
    }

    fun updateLayerBounds(layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float) {
        scope.launch {
            sceneDao.updateLayerBounds(
                layerId = layerId,
                posX = posX.coerceIn(0f, 1.0f),
                posY = posY.coerceIn(0f, 1.0f),
                width = width.coerceIn(0.02f, 1.0f),
                height = height.coerceIn(0.02f, 1.0f),
                scale = scale.coerceIn(0.2f, 3.0f)
            )
        }
    }

    fun centerLayer(layerId: String) {
        scope.launch {
            val layer = activeLayers.value.find { it.id == layerId } ?: return@launch
            val newX = ((1.0f - layer.width) / 2f).coerceAtLeast(0f)
            val newY = ((1.0f - layer.height) / 2f).coerceAtLeast(0f)
            sceneDao.updateLayerTransform(layerId, newX, newY, layer.scale)
        }
    }

    fun fitLayerToScreen(layerId: String) {
        scope.launch {
            sceneDao.updateLayerBounds(
                layerId = layerId,
                posX = 0.0f,
                posY = 0.0f,
                width = 1.0f,
                height = 1.0f,
                scale = 1.0f
            )
        }
    }

    fun deleteLayer(layerId: String) {
        scope.launch {
            sceneDao.deleteLayerById(layerId)
        }
    }

    fun duplicateLayer(layer: SceneLayerEntity) {
        scope.launch {
            val duplicate = layer.copy(
                id = UUID.randomUUID().toString(),
                name = "${layer.name} (Copy)",
                posX = (layer.posX + 0.05f).coerceAtMost(0.8f),
                posY = (layer.posY + 0.05f).coerceAtMost(0.8f),
                zIndex = (activeLayers.value.maxOfOrNull { it.zIndex } ?: 0) + 1
            )
            sceneDao.insertLayer(duplicate)
        }
    }

    fun applySceneTemplate(sceneId: String, templateKey: String) {
        scope.launch {
            val existing = sceneDao.getLayersForScene(sceneId).first()
            existing.forEach { sceneDao.deleteLayer(it) }

            when (templateKey) {
                "game_layout" -> {
                    addLayer(sceneId, LayerType.WEB, "DonationAlerts", "https://www.donationalerts.com/widget/alerts?token=demo", 0.1f, 0.05f, 0.8f, 0.25f)
                    addLayer(sceneId, LayerType.CAMERA_PIP, "FaceCam PiP", "", 0.7f, 0.05f, 0.25f, 0.25f)
                    addLayer(sceneId, LayerType.TEXT, "Streamer Tag", "🔴 LIVE STREAM", 0.05f, 0.9f, 0.4f, 0.08f)
                }
                "chat_layout" -> {
                    addLayer(sceneId, LayerType.WEB, "DonationAlerts", "https://www.donationalerts.com/widget/alerts?token=demo", 0.05f, 0.05f, 0.45f, 0.25f)
                    addLayer(sceneId, LayerType.WEB, "Live Chat", "https://streamlabs.com/widgets/chat-box/v1/", 0.55f, 0.15f, 0.42f, 0.75f, alpha = 0.9f)
                    addLayer(sceneId, LayerType.WEB, "Goal Bar", "https://www.donationalerts.com/widget/goal?token=", 0.05f, 0.85f, 0.45f, 0.12f)
                }
                "brb_layout" -> {
                    addLayer(sceneId, LayerType.TEXT, "Timer", "Стрим начнется через: 05:00", 0.1f, 0.4f, 0.8f, 0.15f)
                    addLayer(sceneId, LayerType.TEXT, "Social", "✈️ Telegram: @streamer", 0.2f, 0.8f, 0.6f, 0.08f)
                }
            }
        }
    }
}
