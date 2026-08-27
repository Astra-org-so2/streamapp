package com.streamapp.features.chat

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KickChatClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _messages = MutableSharedFlow<StreamChatMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<StreamChatMessage> = _messages.asSharedFlow()

    fun connect(chatroomId: String) {
        disconnect()
        val cleanId = chatroomId.trim()
        if (cleanId.isBlank()) return

        val request = Request.Builder()
            .url("wss://ws-us2.pusher.com/app/eb1d5f283081a78b932c?protocol=7&client=js&version=7.6.0&flash=false")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Subscribe to Kick chatroom
                val subscribePayload = JSONObject().apply {
                    put("event", "pusher:subscribe")
                    put("data", JSONObject().apply {
                        put("auth", "")
                        put("channel", "chatrooms.$cleanId.v2")
                    })
                }
                webSocket.send(subscribePayload.toString())
                
                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Система",
                        text = "Подключено к чату Kick (Комната #$cleanId)",
                        userColor = Color(0xFF53FC18)
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val event = root.optString("event")
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
                    // Ignore ping/pong
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Ошибка",
                        text = "Ошибка чата Kick: ${t.localizedMessage ?: "Сбой соединения"}",
                        userColor = Color(0xFFFF453A)
                    )
                )
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
}
