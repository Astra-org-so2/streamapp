package com.streamapp.features.chat

import com.streamapp.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class ChatConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

@Singleton
class ChatManager @Inject constructor(
    private val dispatchers: AppDispatchers
) {
    private val scope = CoroutineScope(dispatchers.io + SupervisorJob())
    private val messageMutex = Mutex()

    val twitchClient = TwitchChatClient(dispatchers)
    val kickClient = KickChatClient(dispatchers)

    private val _messagesList = MutableStateFlow<List<StreamChatMessage>>(emptyList())
    val messagesList: StateFlow<List<StreamChatMessage>> = _messagesList.asStateFlow()

    private val _currentChannel = MutableStateFlow("")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ChatConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ChatConnectionStatus> = _connectionStatus.asStateFlow()

    val isChatConnected: StateFlow<Boolean> = _connectionStatus
        .map { it == ChatConnectionStatus.CONNECTED }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            twitchClient.messages.collect { msg ->
                appendMessage(msg)
                if (msg.platform != ChatPlatform.SYSTEM || msg.text.contains("Подключено")) {
                    _connectionStatus.value = ChatConnectionStatus.CONNECTED
                } else if (msg.text.contains("Ошибка") || msg.text.contains("Не удалось")) {
                    _connectionStatus.value = ChatConnectionStatus.ERROR
                }
            }
        }

        scope.launch {
            kickClient.messages.collect { msg ->
                appendMessage(msg)
                if (msg.platform != ChatPlatform.SYSTEM || msg.text.contains("Подключено")) {
                    _connectionStatus.value = ChatConnectionStatus.CONNECTED
                } else if (msg.text.contains("Ошибка") || msg.text.contains("Не удалось")) {
                    _connectionStatus.value = ChatConnectionStatus.ERROR
                }
            }
        }
    }

    private suspend fun appendMessage(msg: StreamChatMessage) {
        messageMutex.withLock {
            val current = _messagesList.value.toMutableList()
            if (current.size >= 250) {
                current.removeAt(0)
            }
            current.add(msg)
            _messagesList.value = current
        }
    }

    fun connectTwitch(channel: String) {
        _currentChannel.value = channel
        _connectionStatus.value = ChatConnectionStatus.CONNECTING
        twitchClient.connect(channel)
    }

    fun connectKick(chatroomId: String) {
        _currentChannel.value = chatroomId
        _connectionStatus.value = ChatConnectionStatus.CONNECTING
        kickClient.connect(chatroomId)
    }

    fun disconnect() {
        twitchClient.disconnect()
        kickClient.disconnect()
        _connectionStatus.value = ChatConnectionStatus.DISCONNECTED
    }

    fun clearChat() {
        scope.launch {
            messageMutex.withLock {
                _messagesList.value = emptyList()
            }
        }
    }
}
