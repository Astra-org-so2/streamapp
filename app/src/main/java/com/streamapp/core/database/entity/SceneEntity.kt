package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isSelected: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LayerType {
    WEB, IMAGE, TEXT, CAMERA_PIP
}

@Entity(
    tableName = "scene_layers",
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sceneId"])]
)
data class SceneLayerEntity(
    @PrimaryKey
    val id: String,
    val sceneId: String,
    val type: LayerType,
    val name: String,
    val content: String, // URL, text content, image path/uri
    val posX: Float = 0.1f, // Normalized 0.0 - 1.0
    val posY: Float = 0.1f, // Normalized 0.0 - 1.0
    val width: Float = 0.4f, // Normalized 0.0 - 1.0
    val height: Float = 0.25f, // Normalized 0.0 - 1.0
    val scale: Float = 1.0f,
    val alpha: Float = 1.0f,
    val zIndex: Int = 0,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)
