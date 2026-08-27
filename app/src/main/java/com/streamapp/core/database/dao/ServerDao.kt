package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.streamapp.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ServerDao {

    @Query("SELECT * FROM servers ORDER BY isDefault DESC, name ASC, id ASC")
    abstract fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isDefault = 1 ORDER BY name ASC, id ASC LIMIT 1")
    abstract fun getDefaultServer(): Flow<ServerEntity?>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    abstract suspend fun getServerById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertServer(server: ServerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertServers(servers: List<ServerEntity>): List<Long>

    @Update
    abstract suspend fun updateServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    abstract suspend fun deleteServerById(id: String)

    @Query("UPDATE servers SET isDefault = (id = :selectedId) WHERE EXISTS (SELECT 1 FROM servers WHERE id = :selectedId)")
    protected abstract suspend fun updateDefaultServerFlagsIfPresent(selectedId: String): Int

    @Query("UPDATE servers SET isDefault = CASE WHEN id = (SELECT id FROM servers ORDER BY name ASC, id ASC LIMIT 1) THEN 1 ELSE 0 END WHERE (SELECT COUNT(*) FROM servers WHERE isDefault = 1) > 1")
    abstract suspend fun normalizeDefaultServerInvariant()

    @Transaction
    open suspend fun setDefaultServer(id: String): Boolean {
        val affected = updateDefaultServerFlagsIfPresent(id)
        return affected > 0
    }
}
