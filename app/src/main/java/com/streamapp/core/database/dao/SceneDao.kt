package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SceneDao {

    // Scene CRUD
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC, createdAt ASC, id ASC")
    abstract fun getAllScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC, createdAt ASC, id ASC")
    abstract suspend fun getAllScenesSync(): List<SceneEntity>

    @Query("SELECT * FROM scenes WHERE id = :sceneId LIMIT 1")
    abstract suspend fun getSceneById(sceneId: String): SceneEntity?

    @Query("SELECT * FROM scenes WHERE isSelected = 1 ORDER BY orderIndex ASC, createdAt ASC, id ASC LIMIT 1")
    abstract fun getActiveScene(): Flow<SceneEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertScene(scene: SceneEntity): Long

    @Update
    abstract suspend fun updateScene(scene: SceneEntity)

    @Delete
    abstract suspend fun deleteScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :sceneId")
    abstract suspend fun deleteSceneById(sceneId: String)

    @Query("UPDATE scenes SET isSelected = (id = :selectedId)")
    protected abstract suspend fun updateActiveSceneFlags(selectedId: String)

    @Query("UPDATE scenes SET isSelected = (id = :selectedId) WHERE EXISTS (SELECT 1 FROM scenes WHERE id = :selectedId)")
    protected abstract suspend fun updateActiveSceneFlagsIfPresent(selectedId: String): Int

    @Query("UPDATE scenes SET isSelected = CASE WHEN id = (SELECT id FROM scenes ORDER BY orderIndex ASC, createdAt ASC, id ASC LIMIT 1) THEN 1 ELSE 0 END WHERE (SELECT COUNT(*) FROM scenes WHERE isSelected = 1) != 1")
    abstract suspend fun normalizeActiveSceneInvariant()

    @Transaction
    open suspend fun setActiveScene(selectedId: String): Boolean {
        val affected = updateActiveSceneFlagsIfPresent(selectedId)
        return affected > 0
    }

    @Transaction
    open suspend fun createSceneWithSelection(scene: SceneEntity): Boolean {
        val rowId = insertScene(scene)
        if (rowId != -1L) {
            updateActiveSceneFlags(scene.id)
            return true
        }
        return false
    }

    @Transaction
    open suspend fun deleteSceneAndSelectNext(sceneId: String): Boolean {
        val allScenes = getAllScenesSync()
        if (allScenes.size <= 1) return false // Do not delete last remaining scene

        val targetIndex = allScenes.indexOfFirst { it.id == sceneId }
        if (targetIndex == -1) return false

        val wasSelected = allScenes[targetIndex].isSelected

        // Select neighbor: previous scene if available, otherwise next scene
        val fallbackSceneId = if (targetIndex > 0) {
            allScenes[targetIndex - 1].id
        } else if (allScenes.size > 1) {
            allScenes[1].id
        } else {
            null
        }

        deleteLayersForScene(sceneId)
        deleteSceneById(sceneId)

        if (wasSelected && fallbackSceneId != null) {
            updateActiveSceneFlagsIfPresent(fallbackSceneId)
        }
        return true
    }

    // Layer CRUD
    @Query("SELECT * FROM scene_layers WHERE sceneId = :sceneId ORDER BY zIndex ASC, id ASC")
    abstract fun getLayersForScene(sceneId: String): Flow<List<SceneLayerEntity>>

    @Query("SELECT * FROM scene_layers WHERE sceneId = :sceneId ORDER BY zIndex ASC, id ASC")
    abstract suspend fun getLayersForSceneSync(sceneId: String): List<SceneLayerEntity>

    @Query("SELECT MAX(zIndex) FROM scene_layers WHERE sceneId = :sceneId")
    abstract suspend fun getMaxZIndexForScene(sceneId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLayer(layer: SceneLayerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLayers(layers: List<SceneLayerEntity>): List<Long>

    @Update
    abstract suspend fun updateLayer(layer: SceneLayerEntity)

    @Delete
    abstract suspend fun deleteLayer(layer: SceneLayerEntity)

    @Query("DELETE FROM scene_layers WHERE id = :layerId")
    abstract suspend fun deleteLayerById(layerId: String)

    @Query("DELETE FROM scene_layers WHERE sceneId = :sceneId")
    abstract suspend fun deleteLayersForScene(sceneId: String)

    @Transaction
    open suspend fun replaceSceneLayers(sceneId: String, layers: List<SceneLayerEntity>): Boolean {
        val scene = getSceneById(sceneId) ?: return false
        val sanitizedLayers = layers.map { it.sanitized().copy(sceneId = scene.id) }
        deleteLayersForScene(scene.id)
        val inserted = insertLayers(sanitizedLayers)
        return inserted.none { it == -1L }
    }

    @Query("UPDATE scene_layers SET name = :name, content = :content WHERE id = :layerId")
    abstract suspend fun updateLayerContent(layerId: String, name: String, content: String)

    @Query("UPDATE scene_layers SET isVisible = :isVisible WHERE id = :layerId")
    abstract suspend fun setLayerVisibility(layerId: String, isVisible: Boolean)

    @Query("UPDATE scene_layers SET isLocked = :isLocked WHERE id = :layerId")
    abstract suspend fun setLayerLock(layerId: String, isLocked: Boolean)

    @Query("UPDATE scene_layers SET alpha = :alpha WHERE id = :layerId")
    abstract suspend fun setLayerAlpha(layerId: String, alpha: Float)

    @Query("UPDATE scene_layers SET posX = :posX, posY = :posY, width = :width, height = :height, scale = :scale WHERE id = :layerId")
    abstract suspend fun updateLayerBounds(layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float)

    @Query("UPDATE scene_layers SET posX = :posX, posY = :posY, scale = :scale WHERE id = :layerId")
    abstract suspend fun updateLayerTransform(layerId: String, posX: Float, posY: Float, scale: Float)
}
