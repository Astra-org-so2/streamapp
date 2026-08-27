package com.streamapp.core.streaming.client

import android.view.Surface
import com.streamapp.core.common.result.AppResult
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.model.connection.StreamingConnectionState
import com.streamapp.core.model.stream.StreamConfiguration
import com.streamapp.core.model.stream.StreamStatistics
import kotlinx.coroutines.flow.StateFlow

/**
 * Universal StreamingClient interface decoupled from any specific transport.
 * Allows transparent swapping of FakeStreamingClient, WebRtcStreamingClient,
 * MoonlightStreamingClient, etc., without affecting UI or ViewModels.
 */
interface StreamingClient {
    suspend fun connect(config: StreamConfiguration): AppResult<Unit>
    suspend fun disconnect()
    fun connectionState(): StateFlow<StreamingConnectionState>
    fun statistics(): StateFlow<StreamStatistics>
    suspend fun sendInput(event: NormalizedInputEvent)
    fun attachVideoSurface(surface: Surface)
    fun detachVideoSurface()
    fun setStreamVolume(volume: Float)
}
