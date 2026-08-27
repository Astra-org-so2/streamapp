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
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

class TwitchChatClient(
    private val dispatchers: AppDispatchers
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isExplicitDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var currentChannel = ""
    private val clientScope = CoroutineScope(dispatchers.io + SupervisorJob())

    private val _messages = MutableSharedFlow<StreamChatMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<StreamChatMessage> = _messages.asSharedFlow()

    fun connect(channelName: String) {
        val cleanChannel = channelName.trim().lowercase().removePrefix("#")
        if (cleanChannel.isBlank()) return

        currentChannel = cleanChannel
        isExplicitDisconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()

        openWebSocket()
    }

    private fun openWebSocket() {
        if (isExplicitDisconnect || currentChannel.isBlank()) return

        disconnectInternal()

        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                AppLogger.i(LogCategory.NETWORK, "Connected to Twitch IRC WebSocket for #$currentChannel")

                // Request Twitch IRC capabilities (badges, display-name, color)
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
                val anonNick = "justinfan${Random.nextInt(10000, 99999)}"
                webSocket.send("PASS SCHMOOPIE")
                webSocket.send("NICK $anonNick")
                webSocket.send("JOIN #$currentChannel")

                _messages.tryEmit(
                    StreamChatMessage(
                        platform = ChatPlatform.SYSTEM,
                        senderName = "Система",
                        text = "Подключено к чату Twitch: #$currentChannel",
                        userColor = Color(0xFF9146FF)
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Handle IRC lines (may contain multiple lines separated by \r\n)
                text.lines().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.isBlank()) return@forEach

                    // Ping / Pong Heartbeat
                    if (cleanLine.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                        return@forEach
                    }

                    // Handle Server RECONNECT Command
                    if (cleanLine.contains("RECONNECT")) {
                        AppLogger.w(LogCategory.NETWORK, "Twitch requested IRC RECONNECT -> reconnecting")
                        scheduleReconnect()
                        return@forEach
                    }

                    if (cleanLine.contains("PRIVMSG")) {
                        parseTwitchIrcMessage(cleanLine)?.let { msg ->
                            _messages.tryEmit(msg)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.w(LogCategory.NETWORK, "Twitch WebSocket failure: ${t.message}")
                if (!isExplicitDisconnect) {
                    _messages.tryEmit(
                        StreamChatMessage(
                            platform = ChatPlatform.SYSTEM,
                            senderName = "Ошибка",
                            text = "Сбой соединения с Twitch: ${t.localizedMessage ?: "Переподключение..."}",
                            userColor = Color(0xFFFF453A)
                        )
                    )
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i(LogCategory.NETWORK, "Twitch WebSocket closed (code $code: $reason)")
                if (!isExplicitDisconnect && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isExplicitDisconnect || currentChannel.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            reconnectAttempts++
            // Exponential backoff: 1s, 2s, 4s, 8s ... max 30s + jitter
            val baseDelay = min(30000L, (1000L * (1 shl min(reconnectAttempts, 5))))
            val jitter = Random.nextLong(200, 1000)
            val totalDelay = baseDelay + jitter

            AppLogger.i(LogCategory.NETWORK, "Reconnecting to Twitch in ${totalDelay}ms (attempt $reconnectAttempts)")
            delay(totalDelay)
            openWebSocket()
        }
    }

    private fun parseTwitchIrcMessage(raw: String): StreamChatMessage? {
        return try {
            var tagsMap = mapOf<String, String>()
            var line = raw

            // Parse IRC Tags if present (@tag1=val1;tag2=val2)
            if (line.startsWith("@")) {
                val firstSpace = line.indexOf(' ')
                if (firstSpace != -1) {
                    val tagsStr = line.substring(1, firstSpace)
                    tagsMap = tagsStr.split(";").associate { kvPair ->
                        val eq = kvPair.indexOf('=')
                        if (eq != -1) {
                            kvPair.substring(0, eq) to kvPair.substring(eq + 1)
                        } else {
                            kvPair to ""
                        }
                    }
                    line = line.substring(firstSpace + 1)
                }
            }

            val privmsgIdx = line.indexOf("PRIVMSG")
            if (privmsgIdx == -1) return null

            val colonIdx = line.indexOf(" :", privmsgIdx)
            if (colonIdx == -1) return null

            val chatText = line.substring(colonIdx + 2).trim()
            val prefix = if (line.startsWith(":")) line.substring(1, privmsgIdx).trim() else ""
            val rawNick = if (prefix.contains("!")) prefix.substringBefore("!") else prefix

            val sender = tagsMap["display-name"]?.ifBlank { null } ?: rawNick.ifBlank { "Twitch User" }

            val hexColor = tagsMap["color"]?.ifBlank { null }
            val color = hexColor?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    Color(0xFF5AC8FA)
                }
            } ?: Color(0xFF5AC8FA)

            val badges = tagsMap["badges"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
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
            AppLogger.w(LogCategory.NETWORK, "Failed to parse Twitch IRC line: $raw")
            null
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
        currentChannel = ""
        disconnectInternal()
    }
}
