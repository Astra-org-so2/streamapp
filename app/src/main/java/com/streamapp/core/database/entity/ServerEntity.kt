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
    val port: Int = 47990,
    val protocol: String = StreamProtocol.WEBRTC.name,
    val authType: String = AuthType.NONE.name,
    val isDefault: Boolean = false,
    val status: String = ServerStatus.ONLINE.name,
    val latencyMs: Long? = null,
    val serverVersion: String? = null,
    val availableStreamsCount: Int = 0
) {
    fun toDomain(): StreamServer = StreamServer(
        id = id,
        name = name,
        host = host,
        port = if (port in 1..65535) port else 47990,
        protocol = StreamProtocol.fromString(protocol),
        authType = AuthType.fromString(authType),
        isDefault = isDefault,
        status = ServerStatus.fromString(status),
        latencyMs = latencyMs,
        serverVersion = serverVersion,
        availableStreamsCount = availableStreamsCount.coerceAtLeast(0)
    )

    fun sanitized(): ServerEntity = copy(
        port = if (port in 1..65535) port else 47990,
        availableStreamsCount = availableStreamsCount.coerceAtLeast(0)
    )

    companion object {
        fun fromDomain(server: StreamServer): ServerEntity = ServerEntity(
            id = server.id,
            name = server.name,
            host = server.host,
            port = if (server.port in 1..65535) server.port else 47990,
            protocol = server.protocol.name,
            authType = server.authType.name,
            isDefault = server.isDefault,
            status = server.status.name,
            latencyMs = server.latencyMs,
            serverVersion = server.serverVersion,
            availableStreamsCount = server.availableStreamsCount.coerceAtLeast(0)
        )
    }
}
