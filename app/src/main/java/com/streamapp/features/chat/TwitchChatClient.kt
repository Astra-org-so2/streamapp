package com.streamapp.features.chat

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class TwitchChatClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _messages = MutableSharedFlow<StreamChatMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<StreamChatMessage> = _messages.asSharedFlow()

    fun connect(channelName: String) {
        disconnect()
        val cleanChannel = channelName.trim().lowercase().removePrefix("#")
        if (cleanChannel.isBlank()) return

        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Request Twitch capabilities for badges and colors
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
                // Anonymous read-only login
                val anonNick = "justinfan${(10000..99999).random()}"
                webSocket.send("PASS SCHMOOPIE")
                webSocket.send("NICK $anonNick")
                webSocket.send("JOIN #$cleanChannel")
                
                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Система",
                        text = "Подключено к чату Twitch: #$cleanChannel",
                        userColor = Color(0xFF9146FF)
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.startsWith("PING")) {
                    webSocket.send("PONG :tmi.twitch.tv")
                    return
                }

                if (text.contains("PRIVMSG")) {
                    parseTwitchIrcMessage(text)?.let {
                        _messages.tryEmit(it)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Ошибка",
                        text = "Ошибка связи с чатом Twitch: ${t.localizedMessage ?: "Сбой сети"}",
                        userColor = Color(0xFFFF453A)
                    )
                )
            }
        })
    }

    private fun parseTwitchIrcMessage(raw: String): StreamChatMessage? {
        return try {
            var tagsMap = mapOf<String, String>()
            var msgPart = raw

            if (raw.startsWith("@")) {
                val spaceIdx = raw.indexOf(" ")
                val tagsStr = raw.substring(1, spaceIdx)
                tagsMap = tagsStr.split(";").associate {
                    val kv = it.split("=")
                    if (kv.size == 2) kv[0] to kv[1] else kv[0] to ""
                }
                msgPart = raw.substring(spaceIdx + 1)
            }

            val privmsgIdx = msgPart.indexOf("PRIVMSG")
            if (privmsgIdx == -1) return null

            val colonIdx = msgPart.indexOf(" :", privmsgIdx)
            if (colonIdx == -1) return null

            val chatText = msgPart.substring(colonIdx + 2).trim()
            val sender = tagsMap["display-name"]?.ifBlank { null }
                ?: msgPart.substring(1, msgPart.indexOf("!")).trim()

            val hexColor = tagsMap["color"]?.ifBlank { null }
            val color = hexColor?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    Color(0xFF5AC8FA)
                }
            } ?: Color(0xFF5AC8FA)

            val badges = tagsMap["badges"]?.split(",") ?: emptyList()
            val isMod = tagsMap["mod"] == "1" || badges.any { it.startsWith("moderator") || it.startsWith("broadcaster") }
            val isSub = tagsMap["subscriber"] == "1" || badges.any { it.startsWith("subscriber") }

            StreamChatMessage(
                platform = ChatPlatform.TWITCH,
                senderName = sender,
                text = chatText,
                userColor = color,
                badges = badges,
                isSubscriber = isSub,
                isMod = isMod
            )
        } catch (e: Exception) {
            null
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
}
