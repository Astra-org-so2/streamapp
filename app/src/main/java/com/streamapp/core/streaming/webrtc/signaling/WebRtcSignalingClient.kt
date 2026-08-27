package com.streamapp.core.streaming.webrtc.signaling

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.streaming.webrtc.model.SignalingMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcSignalingClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    fun connect(signalingUrl: String) {
        AppLogger.i(LogCategory.NETWORK, "Connecting to WebRTC Signaling server at $signalingUrl")

        val request = Request.Builder().url(signalingUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.i(LogCategory.NETWORK, "WebRTC Signaling channel opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = json.decodeFromString<SignalingMessage>(text)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.NETWORK, "Failed to parse signaling message: $text (${e.message})")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i(LogCategory.NETWORK, "Signaling channel closing: $reason ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e(LogCategory.NETWORK, "Signaling channel failure: ${t.message}", t)
                _incomingMessages.tryEmit(SignalingMessage.ErrorMsg(t.message ?: "Signaling failed"))
            }
        })
    }

    fun send(message: SignalingMessage) {
        val text = json.encodeToString(message)
        webSocket?.send(text)
    }

    fun disconnect() {
        AppLogger.i(LogCategory.NETWORK, "Disconnecting WebRTC Signaling channel")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
