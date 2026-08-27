package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scenes",
    indices = [
        Index(value = ["isSelected"]),
        Index(value = ["orderIndex"])
    ]
)
data class SceneEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isSelected: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LayerType(val code: String) {
    WEB("web"),
    IMAGE("image"),
    TEXT("text"),
    CAMERA_PIP("camera_pip");

    companion object {
        fun fromString(value: String?): LayerType {
            if (value == null) return WEB
            return entries.find {
                it.name.equals(value, ignoreCase = true) || it.code.equals(value, ignoreCase = true)
            } ?: when (value.uppercase()) {
                "CAMERA", "PIP", "WEBCAM" -> CAMERA_PIP
                "TXT", "LABEL" -> TEXT
                "IMG", "PICTURE" -> IMAGE
                "URL", "HTML" -> WEB
                else -> WEB
            }
        }
    }
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
    indices = [
        Index(value = ["sceneId"]),
        Index(value = ["sceneId", "zIndex"])
    ]
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
) {
    fun sanitized(): SceneLayerEntity = copy(
        posX = posX.coerceIn(0.0f, 1.0f),
        posY = posY.coerceIn(0.0f, 1.0f),
        width = width.coerceIn(0.01f, 1.0f),
        height = height.coerceIn(0.01f, 1.0f),
        scale = scale.coerceIn(0.1f, 5.0f),
        alpha = alpha.coerceIn(0.0f, 1.0f)
    )
}
