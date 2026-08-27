package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamapp.core.model.server.AuthType
import com.streamapp.core.model.server.ServerStatus
import com.streamapp.core.model.server.StreamProtocol
import com.streamapp.core.model.server.StreamServer

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val authType: String,
    val isDefault: Boolean,
    val status: String,
    val latencyMs: Long?,
    val serverVersion: String?,
    val availableStreamsCount: Int
) {
    fun toDomain(): StreamServer = StreamServer(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = StreamProtocol.fromString(protocol),
        authType = AuthType.fromString(authType),
        isDefault = isDefault,
        status = ServerStatus.fromString(status),
        latencyMs = latencyMs,
        serverVersion = serverVersion,
        availableStreamsCount = availableStreamsCount
    )

    companion object {
        fun fromDomain(server: StreamServer): ServerEntity = ServerEntity(
            id = server.id,
            name = server.name,
            host = server.host,
            port = server.port,
            protocol = server.protocol.name,
            authType = server.authType.name,
            isDefault = server.isDefault,
            status = server.status.name,
            latencyMs = server.latencyMs,
            serverVersion = server.serverVersion,
            availableStreamsCount = server.availableStreamsCount
        )
    }
}
