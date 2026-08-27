package com.streamapp.features.chat

import androidx.compose.ui.graphics.Color
import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

class KickChatClient(
    private val dispatchers: AppDispatchers
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isExplicitDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var currentChatroomId = ""
    private val clientScope = CoroutineScope(dispatchers.io + SupervisorJob())

    private val _messages = MutableSharedFlow<StreamChatMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<StreamChatMessage> = _messages.asSharedFlow()

    fun connect(chatroomId: String) {
        val cleanId = chatroomId.trim()
        if (cleanId.isBlank()) return

        currentChatroomId = cleanId
        isExplicitDisconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()

        openWebSocket()
    }

    private fun openWebSocket() {
        if (isExplicitDisconnect || currentChatroomId.isBlank()) return

        disconnectInternal()

        val request = Request.Builder()
            .url("wss://ws-us2.pusher.com/app/eb1d5f283081a78b932c?protocol=7&client=js&version=7.6.0&flash=false")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                AppLogger.i(LogCategory.NETWORK, "Connected to Kick Pusher WebSocket for room #$currentChatroomId")

                // Subscribe to Kick chatroom v2 channel
                val subscribePayload = JSONObject().apply {
                    put("event", "pusher:subscribe")
                    put("data", JSONObject().apply {
                        put("auth", "")
                        put("channel", "chatrooms.$currentChatroomId.v2")
                    })
                }
                webSocket.send(subscribePayload.toString())

                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Система",
                        text = "Подключено к чату Kick (Комната #$currentChatroomId)",
                        userColor = Color(0xFF53FC18)
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val event = root.optString("event")

                    // 1. Pusher Heartbeat: handle pusher:ping -> reply pusher:pong
                    if (event == "pusher:ping") {
                        val pong = JSONObject().apply {
                            put("event", "pusher:pong")
                            put("data", JSONObject())
                        }
                        webSocket.send(pong.toString())
                        return
                    }

                    // 2. Chat Message Event
                    if (event == "App\\Events\\ChatMessageEvent") {
                        val dataStr = root.optString("data")
                        val dataObj = JSONObject(dataStr)
                        val content = dataObj.optString("content")
                        val senderObj = dataObj.optJSONObject("sender")
                        val username = senderObj?.optString("username") ?: "Kick Viewer"
                        val identityObj = senderObj?.optJSONObject("identity")
                        val hexColor = identityObj?.optString("color") ?: "#53FC18"

                        val color = try {
                            Color(android.graphics.Color.parseColor(hexColor))
                        } catch (e: Exception) {
                            Color(0xFF53FC18)
                        }

                        _messages.tryEmit(
                            StreamChatMessage(
                                platform = ChatPlatform.KICK,
                                senderName = username,
                                text = content,
                                userColor = color
                            )
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.NETWORK, "Error parsing Kick Pusher message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.w(LogCategory.NETWORK, "Kick WebSocket failure: ${t.message}")
                if (!isExplicitDisconnect) {
                    _messages.tryEmit(
                        StreamChatMessage(
                            platform = ChatPlatform.SYSTEM,
                            senderName = "Ошибка",
                            text = "Сбой соединения с Kick: ${t.localizedMessage ?: "Переподключение..."}",
                            userColor = Color(0xFFFF453A)
                        )
                    )
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i(LogCategory.NETWORK, "Kick WebSocket closed (code $code: $reason)")
                if (!isExplicitDisconnect && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isExplicitDisconnect || currentChatroomId.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            reconnectAttempts++
            val baseDelay = min(30000L, (1000L * (1 shl min(reconnectAttempts, 5))))
            val jitter = Random.nextLong(200, 1000)
            val totalDelay = baseDelay + jitter

            AppLogger.i(LogCategory.NETWORK, "Reconnecting to Kick in ${totalDelay}ms (attempt $reconnectAttempts)")
            delay(totalDelay)
            openWebSocket()
        }
    }

    private fun disconnectInternal() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            // Ignore
        }
        webSocket = null
    }

    fun disconnect() {
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        currentChatroomId = ""
        disconnectInternal()
    }
}
