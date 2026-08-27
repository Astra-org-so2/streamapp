package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.streamapp.core.database.entity.DestinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY name ASC, id ASC")
    fun getAllDestinations(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE id = :id LIMIT 1")
    fun getDestinationById(id: String): Flow<DestinationEntity?>

    @Query("SELECT * FROM destinations WHERE isEnabled = 1 ORDER BY name ASC, id ASC")
    fun getEnabledDestinations(): Flow<List<DestinationEntity>>

    @Upsert
    suspend fun upsertDestination(destination: DestinationEntity): Long

    @Update
    suspend fun updateDestination(destination: DestinationEntity)

    @Delete
    suspend fun deleteDestination(destination: DestinationEntity)
}
