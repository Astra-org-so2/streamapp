package com.streamapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.streamapp.core.database.dao.GameDao
import com.streamapp.core.database.dao.GamepadLayoutDao
import com.streamapp.core.database.dao.ServerDao
import com.streamapp.core.database.entity.GameEntity
import com.streamapp.core.database.entity.GamepadLayoutEntity
import com.streamapp.core.database.entity.ServerEntity

@Database(
    entities = [
        ServerEntity::class,
        GameEntity::class,
        GamepadLayoutEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StreamDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun gameDao(): GameDao
    abstract fun gamepadLayoutDao(): GamepadLayoutDao
}
