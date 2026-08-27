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
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class WebRtcReconnectManager @Inject constructor() {

    private val maxAttempts = 5
    private var currentAttempt = 0

    private val _reconnectState = MutableStateFlow<StreamingConnectionState>(StreamingConnectionState.Connected)
    val reconnectState: StateFlow<StreamingConnectionState> = _reconnectState.asStateFlow()

    fun reset() {
        currentAttempt = 0
        _reconnectState.value = StreamingConnectionState.Connected
    }

    suspend fun attemptReconnect(onPerformIceRestart: suspend () -> Boolean): Boolean {
        currentAttempt++
        if (currentAttempt > maxAttempts) {
            AppLogger.e(LogCategory.STREAMING, "Maximum reconnection attempts ($maxAttempts) exhausted")
            _reconnectState.value = StreamingConnectionState.Error(
                AppError.ConnectionTimeout("Unable to restore streaming connection after $maxAttempts attempts")
            )
            return false
        }

        val backoffDelayMs = (1000L * 2.0.pow((currentAttempt - 1).toDouble())).toLong()
        val clampedDelay = min(backoffDelayMs, 8000L)

        AppLogger.w(
            LogCategory.STREAMING,
            "Reconnection attempt $currentAttempt/$maxAttempts (Waiting ${clampedDelay}ms before ICE restart)"
        )
        _reconnectState.value = StreamingConnectionState.Reconnecting(currentAttempt, maxAttempts)

        delay(clampedDelay)
        val success = onPerformIceRestart()
        if (success) {
            AppLogger.i(LogCategory.STREAMING, "Reconnection successful on attempt $currentAttempt")
            reset()
            _reconnectState.value = StreamingConnectionState.Connected
            return true
        }

        return false
    }
}
