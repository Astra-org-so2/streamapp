package com.streamapp.features.chat

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class ChatPlatform {
    TWITCH, KICK, YOUTUBE, SYSTEM
}

data class StreamChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val platform: ChatPlatform,
    val senderName: String,
    val text: String,
    val userColor: Color = Color(0xFF5AC8FA),
    val badges: List<String> = emptyList(),
    val isSubscriber: Boolean = false,
    val isMod: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
