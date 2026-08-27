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
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC, createdAt ASC")
    abstract fun getAllScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC, createdAt ASC")
    abstract suspend fun getAllScenesSync(): List<SceneEntity>

    @Query("SELECT * FROM scenes WHERE id = :sceneId LIMIT 1")
    abstract suspend fun getSceneById(sceneId: String): SceneEntity?

    @Query("SELECT * FROM scenes WHERE isSelected = 1 LIMIT 1")
    abstract fun getActiveScene(): Flow<SceneEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertScene(scene: SceneEntity)

    @Update
    abstract suspend fun updateScene(scene: SceneEntity)

    @Delete
    abstract suspend fun deleteScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :sceneId")
    abstract suspend fun deleteSceneById(sceneId: String)

    @Query("UPDATE scenes SET isSelected = CASE WHEN id = :selectedId THEN 1 ELSE 0 END")
    abstract suspend fun setActiveScene(selectedId: String)

    @Transaction
    open suspend fun createSceneWithSelection(scene: SceneEntity) {
        insertScene(scene)
        setActiveScene(scene.id)
    }

    @Transaction
    open suspend fun deleteSceneAndSelectFallback(sceneId: String): Boolean {
        val all = getAllScenesSync()
        if (all.size <= 1) return false
        deleteLayersForScene(sceneId)
        deleteSceneById(sceneId)
        val remaining = getAllScenesSync()
        if (remaining.isNotEmpty() && remaining.none { it.isSelected }) {
            setActiveScene(remaining.first().id)
        }
        return true
    }

    // Layer CRUD
    @Query("SELECT * FROM scene_layers WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    abstract fun getLayersForScene(sceneId: String): Flow<List<SceneLayerEntity>>

    @Query("SELECT * FROM scene_layers WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    abstract suspend fun getLayersForSceneSync(sceneId: String): List<SceneLayerEntity>

    @Query("SELECT MAX(zIndex) FROM scene_layers WHERE sceneId = :sceneId")
    abstract suspend fun getMaxZIndexForScene(sceneId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLayer(layer: SceneLayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLayers(layers: List<SceneLayerEntity>)

    @Update
    abstract suspend fun updateLayer(layer: SceneLayerEntity)

    @Delete
    abstract suspend fun deleteLayer(layer: SceneLayerEntity)

    @Query("DELETE FROM scene_layers WHERE id = :layerId")
    abstract suspend fun deleteLayerById(layerId: String)

    @Query("DELETE FROM scene_layers WHERE sceneId = :sceneId")
    abstract suspend fun deleteLayersForScene(sceneId: String)

    @Transaction
    open suspend fun replaceSceneLayers(sceneId: String, layers: List<SceneLayerEntity>) {
        val scene = getSceneById(sceneId) ?: return
        deleteLayersForScene(scene.id)
        insertLayers(layers)
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
