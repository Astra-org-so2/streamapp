package com.streamapp.core.database.dao

import androidx.room.*
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {

    // Scene CRUD
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC, createdAt ASC")
    fun getAllScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE isSelected = 1 LIMIT 1")
    fun getActiveScene(): Flow<SceneEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Delete
    suspend fun deleteScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :sceneId")
    suspend fun deleteSceneById(sceneId: String)

    @Query("UPDATE scenes SET isSelected = CASE WHEN id = :selectedId THEN 1 ELSE 0 END")
    suspend fun setActiveScene(selectedId: String)

    // Layer CRUD
    @Query("SELECT * FROM scene_layers WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    fun getLayersForScene(sceneId: String): Flow<List<SceneLayerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: SceneLayerEntity)

    @Update
    suspend fun updateLayer(layer: SceneLayerEntity)

    @Delete
    suspend fun deleteLayer(layer: SceneLayerEntity)

    @Query("DELETE FROM scene_layers WHERE id = :layerId")
    suspend fun deleteLayerById(layerId: String)

    @Query("UPDATE scene_layers SET name = :name, content = :content WHERE id = :layerId")
    suspend fun updateLayerContent(layerId: String, name: String, content: String)

    @Query("UPDATE scene_layers SET isVisible = :isVisible WHERE id = :layerId")
    suspend fun setLayerVisibility(layerId: String, isVisible: Boolean)

    @Query("UPDATE scene_layers SET isLocked = :isLocked WHERE id = :layerId")
    suspend fun setLayerLock(layerId: String, isLocked: Boolean)

    @Query("UPDATE scene_layers SET alpha = :alpha WHERE id = :layerId")
    suspend fun setLayerAlpha(layerId: String, alpha: Float)

    @Query("UPDATE scene_layers SET posX = :posX, posY = :posY, width = :width, height = :height, scale = :scale WHERE id = :layerId")
    suspend fun updateLayerBounds(layerId: String, posX: Float, posY: Float, width: Float, height: Float, scale: Float)

    @Query("UPDATE scene_layers SET posX = :posX, posY = :posY, scale = :scale WHERE id = :layerId")
    suspend fun updateLayerTransform(layerId: String, posX: Float, posY: Float, scale: Float)
}
