package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamapp.core.database.entity.GamepadLayoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GamepadLayoutDao {
    @Query("SELECT * FROM gamepad_layouts")
    fun getAllLayouts(): Flow<List<GamepadLayoutEntity>>

    @Query("SELECT * FROM gamepad_layouts WHERE id = :id")
    suspend fun getLayoutById(id: String): GamepadLayoutEntity?

    @Query("SELECT * FROM gamepad_layouts WHERE isDefault = 1 LIMIT 1")
    fun getDefaultLayout(): Flow<GamepadLayoutEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: GamepadLayoutEntity)

    @Update
    suspend fun updateLayout(layout: GamepadLayoutEntity)

    @Query("DELETE FROM gamepad_layouts WHERE id = :id")
    suspend fun deleteLayoutById(id: String)
}
