package com.streamapp.core.common.result

import com.streamapp.core.model.stream.VideoCodec

/**
 * Domain-level error hierarchy representing all possible failure conditions in StreamApp.
 * UI receives a mapped [UiError] with human-readable localized messages.
 */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class NetworkError(
        override val message: String,
        val isTimeout: Boolean = false,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class AuthenticationError(
        override val message: String,
        val requiresPin: Boolean = false
    ) : AppError(message)

    data class ServerUnavailable(
        override val message: String,
        val host: String,
        val port: Int
    ) : AppError(message)

    data class StreamingError(
        override val message: String,
        val isRecoverable: Boolean = true,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class CodecUnavailable(
        val codec: VideoCodec,
        val reason: String = "Hardware decoder not supported by device"
    ) : AppError("Codec $codec unavailable: $reason")

    data class ConnectionTimeout(
        override val message: String = "Connection to stream server timed out"
    ) : AppError(message)

    data class PermissionDenied(
        val permission: String,
        override val message: String = "Required permission $permission was denied"
    ) : AppError(message)

    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)
}
