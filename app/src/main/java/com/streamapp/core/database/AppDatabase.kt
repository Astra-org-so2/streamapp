package com.streamapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.streamapp.core.database.converter.RoomTypeConverters
import com.streamapp.core.database.dao.AudioTrackDao
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.dao.SceneDao
import com.streamapp.core.database.entity.AudioTrackEntity
import com.streamapp.core.database.entity.DestinationEntity
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.database.entity.SceneLayerEntity
import androidx.room.TypeConverters

@Database(
    entities = [
        DestinationEntity::class,
        SceneEntity::class,
        SceneLayerEntity::class,
        AudioTrackEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun destinationDao(): DestinationDao
    abstract fun sceneDao(): SceneDao
    abstract fun audioTrackDao(): AudioTrackDao
}
