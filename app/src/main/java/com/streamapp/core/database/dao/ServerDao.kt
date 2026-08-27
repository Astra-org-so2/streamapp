package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
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

    @Upsert
    abstract suspend fun upsertServer(server: ServerEntity): Long

    @Upsert
    abstract suspend fun upsertServers(servers: List<ServerEntity>): List<Long>

    @Update
    abstract suspend fun updateServer(server: ServerEntity)

    @Query("UPDATE servers SET name = :name, host = :host, port = :port, protocol = :protocol, authType = :authType WHERE id = :id")
    abstract suspend fun updateServerConfig(id: String, name: String, host: String, port: Int, protocol: String, authType: String): Int

    @Query("UPDATE servers SET status = :status, latencyMs = :latencyMs, serverVersion = :serverVersion, availableStreamsCount = :availableStreamsCount WHERE id = :id")
    abstract suspend fun updateServerTelemetry(id: String, status: String, latencyMs: Long?, serverVersion: String?, availableStreamsCount: Int): Int

    @Query("DELETE FROM servers WHERE id = :id")
    abstract suspend fun deleteServerById(id: String)

    @Query("UPDATE servers SET isDefault = (id = :selectedId) WHERE EXISTS (SELECT 1 FROM servers WHERE id = :selectedId)")
    protected abstract suspend fun updateDefaultServerFlagsIfPresent(selectedId: String): Int

    @Query("UPDATE servers SET isDefault = CASE WHEN id = (SELECT id FROM servers ORDER BY name ASC, id ASC LIMIT 1) THEN 1 ELSE 0 END WHERE (SELECT COUNT(*) FROM servers WHERE isDefault = 1) != 1 AND (SELECT COUNT(*) FROM servers) > 0")
    abstract suspend fun normalizeDefaultServerInvariant()

    @Transaction
    open suspend fun setDefaultServer(id: String): Boolean {
        val affected = updateDefaultServerFlagsIfPresent(id)
        return affected > 0
    }

    @Transaction
    open suspend fun deleteServerAndSelectNext(id: String): Boolean {
        val server = getServerById(id) ?: return false
        val wasDefault = server.isDefault
        deleteServerById(id)
        if (wasDefault) {
            normalizeDefaultServerInvariant()
        }
        return true
    }
}
