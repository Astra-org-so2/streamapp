package com.streamapp.features.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val twitchClient = TwitchChatClient()
    val kickClient = KickChatClient()

    private val _messagesList = MutableStateFlow<List<StreamChatMessage>>(emptyList())
    val messagesList: StateFlow<List<StreamChatMessage>> = _messagesList.asStateFlow()

    private val _currentChannel = MutableStateFlow("")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _isChatConnected = MutableStateFlow(false)
    val isChatConnected: StateFlow<Boolean> = _isChatConnected.asStateFlow()

    init {
        scope.launch {
            twitchClient.messages.collect { msg ->
                appendMessage(msg)
                if (msg.platform != ChatPlatform.SYSTEM || msg.text.contains("Подключено")) {
                    _isChatConnected.value = true
                }
            }
        }

        scope.launch {
            kickClient.messages.collect { msg ->
                appendMessage(msg)
                if (msg.platform != ChatPlatform.SYSTEM || msg.text.contains("Подключено")) {
                    _isChatConnected.value = true
                }
            }
        }
    }

    private fun appendMessage(msg: StreamChatMessage) {
        val current = _messagesList.value.toMutableList()
        if (current.size > 200) {
            current.removeAt(0)
        }
        current.add(msg)
        _messagesList.value = current
    }

    fun connectTwitch(channel: String) {
        _currentChannel.value = channel
        twitchClient.connect(channel)
    }

    fun connectKick(chatroomId: String) {
        _currentChannel.value = chatroomId
        kickClient.connect(chatroomId)
    }

    fun disconnect() {
        twitchClient.disconnect()
        kickClient.disconnect()
        _isChatConnected.value = false
    }

    fun clearChat() {
        _messagesList.value = emptyList()
    }
}
