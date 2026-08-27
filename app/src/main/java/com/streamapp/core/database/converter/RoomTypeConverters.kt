package com.streamapp.core.database.converter

import androidx.room.TypeConverter
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.model.Platform

class RoomTypeConverters {

    @TypeConverter
    fun fromLayerType(type: LayerType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toLayerType(value: String?): LayerType? {
        return value?.let {
            try {
                LayerType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                LayerType.WEB
            }
        }
    }

    @TypeConverter
    fun fromPlatform(platform: Platform?): String? {
        return platform?.name
    }

    @TypeConverter
    fun toPlatform(value: String?): Platform? {
        return value?.let {
            try {
                Platform.valueOf(it)
            } catch (e: IllegalArgumentException) {
                Platform.CUSTOM
            }
        }
    }
}
