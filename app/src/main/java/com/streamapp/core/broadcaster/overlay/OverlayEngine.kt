package com.streamapp.core.broadcaster.overlay

import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.database.dao.SceneDao
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayEngine @Inject constructor(
    private val sceneDao: SceneDao,
    private val dispatchers: AppDispatchers
) {
    private var engineScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    val allScenes: StateFlow<List<SceneEntity>> = sceneDao.getAllScenes()
        .stateIn(engineScope, SharingStarted.Eagerly, emptyList())

    val activeScene: StateFlow<SceneEntity?> = sceneDao.getActiveScene()
        .stateIn(engineScope, SharingStarted.Eagerly, null)

    private val _activeLayers = MutableStateFlow<List<SceneLayerEntity>>(emptyList())
    val activeLayers: StateFlow<List<SceneLayerEntity>> = _activeLayers.asStateFlow()

    init {
        engineScope.launch {
            allScenes.collect { scenes ->
                if (scenes.isEmpty()) {
                    // Seed initial default scene
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
                        content = "https://www.donationalerts.com/widget/alerts",
                        posX = 0.05f,
                        posY = 0.05f,
                        width = 0.9f,
                        height = 0.3f,
                        zIndex = 0
                    )
                    sceneDao.insertLayer(defaultAlertLayer)
                } else {
                    // Enforce DB invariant: exactly 1 active selected scene
                    sceneDao.normalizeActiveSceneInvariant()
                }
            }
        }

        engineScope.launch {
            activeScene.collectLatest { scene ->
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
        engineScope.launch {
            sceneDao.setActiveScene(sceneId)
        }
    }

    fun createScene(name: String) {
        engineScope.launch {
            val count = allScenes.value.size
            val newScene = SceneEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                isSelected = true,
                orderIndex = count
            )
            sceneDao.createSceneWithSelection(newScene)
        }
    }

    fun deleteScene(sceneId: String) {
        engineScope.launch {
            sceneDao.deleteSceneAndSelectNext(sceneId)
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
        engineScope.launch {
            val scene = sceneDao.getSceneById(sceneId)
            if (scene == null) {
                AppLogger.e(LogCategory.DATABASE, "Cannot add layer: Scene $sceneId does not exist")
                return@launch
            }

            val maxZ = sceneDao.getMaxZIndexForScene(sceneId) ?: 0
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
                zIndex = maxZ + 1
            )
            sceneDao.insertLayer(layer)
        }
    }

    fun updateLayerContent(layerId: String, name: String, content: String) {
        engineScope.launch {
            sceneDao.updateLayerContent(layerId, name, content)
        }
    }

    fun toggleLayerVisibility(layerId: String, currentVisible: Boolean) {
        engineScope.launch {
            sceneDao.setLayerVisibility(layerId, !currentVisible)
        }
    }

    fun toggleLayerLock(layerId: String, currentLocked: Boolean) {
        engineScope.launch {
            sceneDao.setLayerLock(layerId, !currentLocked)
        }
    }

    fun updateLayerAlpha(layerId: String, alpha: Float) {
        engineScope.launch {
            sceneDao.setLayerAlpha(layerId, alpha.coerceIn(0.05f, 1.0f))
        }
    }

    fun updateLayerBounds(layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float) {
        engineScope.launch {
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
        engineScope.launch {
            val layer = activeLayers.value.find { it.id == layerId } ?: return@launch
            val newX = ((1.0f - layer.width * layer.scale) / 2f).coerceAtLeast(0f)
            val newY = ((1.0f - layer.height * layer.scale) / 2f).coerceAtLeast(0f)
            sceneDao.updateLayerTransform(layerId, newX, newY, layer.scale)
        }
    }

    fun fitLayerToScreen(layerId: String) {
        engineScope.launch {
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
        engineScope.launch {
            sceneDao.deleteLayerById(layerId)
        }
    }

    fun duplicateLayer(layer: SceneLayerEntity) {
        engineScope.launch {
            val maxX = (1.0f - layer.width * layer.scale).coerceAtLeast(0f)
            val maxY = (1.0f - layer.height * layer.scale).coerceAtLeast(0f)
            val newX = (layer.posX + 0.05f).coerceIn(0f, maxX)
            val newY = (layer.posY + 0.05f).coerceIn(0f, maxY)
            val maxZ = sceneDao.getMaxZIndexForScene(layer.sceneId) ?: 0

            val duplicate = layer.copy(
                id = UUID.randomUUID().toString(),
                name = "${layer.name} (Copy)",
                posX = newX,
                posY = newY,
                zIndex = maxZ + 1
            )
            sceneDao.insertLayer(duplicate)
        }
    }

    fun applySceneTemplate(sceneId: String, templateKey: String) {
        engineScope.launch {
            val scene = sceneDao.getSceneById(sceneId)
            if (scene == null) {
                AppLogger.e(LogCategory.DATABASE, "Cannot apply template: Scene $sceneId does not exist")
                return@launch
            }

            val layersToInsert = when (templateKey) {
                "game_layout" -> listOf(
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.WEB,
                        name = "DonationAlerts",
                        content = "https://www.donationalerts.com/widget/alerts",
                        posX = 0.1f,
                        posY = 0.05f,
                        width = 0.8f,
                        height = 0.25f,
                        zIndex = 0
                    ),
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.CAMERA_PIP,
                        name = "FaceCam PiP",
                        content = "",
                        posX = 0.7f,
                        posY = 0.05f,
                        width = 0.25f,
                        height = 0.25f,
                        zIndex = 1
                    ),
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.TEXT,
                        name = "Streamer Tag",
                        content = "🔴 LIVE STREAM",
                        posX = 0.05f,
                        posY = 0.9f,
                        width = 0.4f,
                        height = 0.08f,
                        zIndex = 2
                    )
                )
                "chat_layout" -> listOf(
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.WEB,
                        name = "DonationAlerts",
                        content = "https://www.donationalerts.com/widget/alerts",
                        posX = 0.05f,
                        posY = 0.05f,
                        width = 0.45f,
                        height = 0.25f,
                        zIndex = 0
                    ),
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.WEB,
                        name = "Live Chat",
                        content = "https://streamlabs.com/widgets/chat-box/v1/",
                        posX = 0.55f,
                        posY = 0.15f,
                        width = 0.42f,
                        height = 0.75f,
                        alpha = 0.9f,
                        zIndex = 1
                    ),
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.WEB,
                        name = "Goal Bar",
                        content = "https://www.donationalerts.com/widget/goal",
                        posX = 0.05f,
                        posY = 0.85f,
                        width = 0.45f,
                        height = 0.12f,
                        zIndex = 2
                    )
                )
                "brb_layout" -> listOf(
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.TEXT,
                        name = "Timer",
                        content = "Стрим начнется через: 05:00",
                        posX = 0.1f,
                        posY = 0.4f,
                        width = 0.8f,
                        height = 0.15f,
                        zIndex = 0
                    ),
                    SceneLayerEntity(
                        id = UUID.randomUUID().toString(),
                        sceneId = sceneId,
                        type = LayerType.TEXT,
                        name = "Social",
                        content = "✈️ Telegram: @streamer",
                        posX = 0.2f,
                        posY = 0.8f,
                        width = 0.6f,
                        height = 0.08f,
                        zIndex = 1
                    )
                )
                else -> emptyList()
            }

            sceneDao.replaceSceneLayers(sceneId, layersToInsert)
        }
    }

    fun release() {
        engineScope.cancel()
    }
}
