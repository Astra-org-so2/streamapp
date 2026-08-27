package com.streamapp.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ChatMessage(val id: String, val sender: String, val platform: String, val message: String, val translation: String? = null)

@Composable
fun ChatOverlay(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        items(messages) { msg ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row {
                    Text(
                        text = "[${msg.platform}] ${msg.sender}: ",
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = msg.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (msg.translation != null) {
                    Text(
                        text = "Translated: ${msg.translation}",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
