package com.streamapp.core.streaming.webrtc.reconnect

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.common.result.AppError
import com.streamapp.core.model.connection.StreamingConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

/**
 * Session-scoped Reconnection Manager for WebRTC sessions.
 * Manages exponential backoff, ICE restart attempts, and state transitions.
 */
class WebRtcReconnectManager @Inject constructor() {

    private val maxAttempts = 5
    private var currentAttempt = 0
    private val stateLock = Any()

    private val _reconnectState = MutableStateFlow<StreamingConnectionState>(StreamingConnectionState.Connected)
    val reconnectState: StateFlow<StreamingConnectionState> = _reconnectState.asStateFlow()

    fun reset() = synchronized(stateLock) {
        currentAttempt = 0
        _reconnectState.value = StreamingConnectionState.Connected
    }

    suspend fun attemptReconnect(onPerformIceRestart: suspend () -> Boolean): Boolean {
        while (true) {
            val attempt = synchronized(stateLock) {
                if (currentAttempt >= maxAttempts) return@synchronized null
                currentAttempt++
                currentAttempt
            } ?: break

            val backoffDelayMs = (1000L * 2.0.pow((attempt - 1).toDouble())).toLong()
            val clampedDelay = min(backoffDelayMs, 8000L)

            AppLogger.w(
                LogCategory.STREAMING,
                "Reconnection attempt $attempt/$maxAttempts (Waiting ${clampedDelay}ms before ICE restart handshake)"
            )
            _reconnectState.value = StreamingConnectionState.Reconnecting(attempt, maxAttempts)

            delay(clampedDelay)
            try {
                val success = onPerformIceRestart()
                if (success) {
                    AppLogger.i(LogCategory.STREAMING, "WebRTC reconnection confirmed successful on attempt $attempt")
                    reset()
                    return true
                } else {
                    AppLogger.w(LogCategory.STREAMING, "WebRTC reconnection attempt $attempt was rejected by server")
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.STREAMING, "WebRTC reconnection attempt $attempt threw exception: ${e.message}")
            }
        }

        AppLogger.e(LogCategory.STREAMING, "Maximum WebRTC reconnection attempts ($maxAttempts) exhausted")
        _reconnectState.value = StreamingConnectionState.Error(
            AppError.ConnectionTimeout("Unable to restore streaming connection after $maxAttempts attempts")
        )
        return false
    }
}
