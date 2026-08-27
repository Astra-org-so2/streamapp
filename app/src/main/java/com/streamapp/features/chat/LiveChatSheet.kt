package com.streamapp.features.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamapp.core.designsystem.components.IosCard
import com.streamapp.core.designsystem.components.IosSegmentedControl
import com.streamapp.core.designsystem.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatSheet(
    chatManager: ChatManager,
    onDismiss: () -> Unit
) {
    val messages by chatManager.messagesList.collectAsState()
    val isConnected by chatManager.isChatConnected.collectAsState()
    var selectedPlatform by remember { mutableStateOf(ChatPlatform.TWITCH) }
    var channelInput by remember { mutableStateOf("") }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IosCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(IosLabelTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedPlatform == ChatPlatform.TWITCH) Color(0xFF9146FF) else Color(0xFF53FC18)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Живой Чат Трансляции", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary)
                        Text(if (isConnected) "🟢 Подключено (WebSocket Live)" else "⚪ Оффлайн", style = MaterialTheme.typography.bodySmall, color = if (isConnected) IosGreen else IosLabelSecondary)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = IosLabelSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Platform Selector
            IosSegmentedControl(
                items = listOf(ChatPlatform.TWITCH, ChatPlatform.KICK),
                selectedItem = selectedPlatform,
                onItemSelected = { selectedPlatform = it },
                itemLabel = {
                    when (it) {
                        ChatPlatform.TWITCH -> "Twitch IRC"
                        ChatPlatform.KICK -> "Kick Live"
                        else -> ""
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            // Connect Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    placeholder = {
                        Text(if (selectedPlatform == ChatPlatform.TWITCH) "Канал (напр. shroud)" else "Chatroom ID (напр. 12345)", color = IosLabelSecondary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosGlassBorder,
                        focusedContainerColor = IosCardElevated,
                        unfocusedContainerColor = IosCardElevated
                    ),
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = {
                        if (channelInput.isNotBlank()) {
                            if (selectedPlatform == ChatPlatform.TWITCH) {
                                chatManager.connectTwitch(channelInput)
                            } else {
                                chatManager.connectKick(channelInput)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = IosBlue,
                    modifier = Modifier.height(52.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Войти", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Chat Messages Feed
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IosCardElevated,
                border = BorderStroke(0.5.dp, IosGlassBorder),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Введите имя канала и нажмите «Войти», чтобы читать чат зрителей в реальном времени.",
                            color = IosLabelSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        reverseLayout = true
                    ) {
                        items(messages.reversed(), key = { it.id }) { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = timeFormat.format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = IosLabelTertiary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                if (msg.isMod) {
                                    Text("⚔️", fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
                                }
                                Text(
                                    text = "${msg.senderName}: ",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = msg.userColor
                                )
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosLabelPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
