package com.streamapp.core.model.connection

import com.streamapp.core.common.result.AppError

sealed interface StreamingConnectionState {
    data object Disconnected : StreamingConnectionState
    data object Connecting : StreamingConnectionState
    data object Negotiating : StreamingConnectionState
    data object GatheringCandidates : StreamingConnectionState
    data object Connected : StreamingConnectionState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int = 5) : StreamingConnectionState
    data class Error(val error: AppError) : StreamingConnectionState

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting || this is Negotiating || this is GatheringCandidates || this is Reconnecting
}
