package com.streamapp.features.destinations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamapp.core.database.entity.DestinationEntity
import com.streamapp.core.designsystem.components.IosCard
import com.streamapp.core.designsystem.components.IosHeader
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.model.DestinationConnectionState
import com.streamapp.core.model.Platform

@Composable
fun DestinationsScreen(viewModel: DestinationsViewModel = hiltViewModel()) {
    val destinations by viewModel.destinations.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // iOS Header
        IosHeader(
            title = "Платформы",
            subtitle = "Управление RTMP серверами и ключами стрима",
            trailingAction = {
                Surface(
                    onClick = { showAddDialog = true },
                    shape = CircleShape,
                    color = IosBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, IosBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = IosBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // Error banner if any
        errorMessage?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = IosRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, IosRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = IosRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = IosRed,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = IosRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (destinations.isEmpty()) {
                item {
                    IosCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет добавленных платформ.\nНажмите «+ Добавить», чтобы подключить Twitch, YouTube, Kick или VK.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosLabelSecondary
                            )
                        }
                    }
                }
            } else {
                items(destinations, key = { it.entity.id }) { item ->
                    IosDestinationCard(
                        item = item,
                        onToggle = { viewModel.toggleDestination(item.entity) },
                        onDelete = { viewModel.deleteDestination(item.entity) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddDestinationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { platform, name, rtmpUrl, streamKey ->
                viewModel.addDestination(platform, name, rtmpUrl, streamKey)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun IosDestinationCard(
    item: DestinationUiItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val destination = item.entity
    val platformColor = when (destination.platform) {
        Platform.TWITCH -> Color(0xFF9146FF)
        Platform.YOUTUBE -> Color(0xFFFF0000)
        Platform.KICK -> Color(0xFF53FC18)
        Platform.TIKTOK -> Color(0xFF00F2FE)
        Platform.CUSTOM -> IosBlue
    }

    val (statusLabel, statusColor) = when (item.connectionState) {
        DestinationConnectionState.LIVE -> "В ЭФИРЕ" to IosRed
        DestinationConnectionState.CONNECTING -> "ПОДКЛЮЧЕНИЕ..." to IosOrange
        DestinationConnectionState.READY -> "ГОТОВ К ЭФИРУ" to IosGreen
        DestinationConnectionState.ERROR -> "ОШИБКА" to IosRed
        DestinationConnectionState.DISABLED -> "Отключен" to IosLabelSecondary
    }

    IosCard(
        border = when (item.connectionState) {
            DestinationConnectionState.LIVE -> BorderStroke(1.5.dp, IosRed)
            DestinationConnectionState.CONNECTING -> BorderStroke(1.dp, IosOrange)
            DestinationConnectionState.READY -> BorderStroke(1.dp, IosGreen.copy(alpha = 0.6f))
            DestinationConnectionState.ERROR -> BorderStroke(1.dp, IosRed.copy(alpha = 0.6f))
            DestinationConnectionState.DISABLED -> BorderStroke(0.5.dp, IosGlassBorder)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(platformColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (destination.platform == Platform.KICK) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = IosLabelPrimary
                    )
                    Text(
                        text = "${destination.platform.name} • $statusLabel",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (destination.isEnabled) FontWeight.SemiBold else FontWeight.Normal),
                        color = statusColor
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = IosRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Switch(
                    checked = destination.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IosGreen,
                        uncheckedTrackColor = IosCardElevated
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDestinationDialog(
    onDismiss: () -> Unit,
    onAdd: (Platform, String, String, String) -> Unit
) {
    var platform by remember { mutableStateOf(Platform.TWITCH) }
    var name by remember { mutableStateOf(Platform.TWITCH.defaultName) }
    var rtmpUrl by remember { mutableStateOf(Platform.TWITCH.defaultRtmpUrl) }
    var streamKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val isFormValid = name.isNotBlank() &&
            (rtmpUrl.trim().startsWith("rtmp://", ignoreCase = true) || rtmpUrl.trim().startsWith("rtmps://", ignoreCase = true)) &&
            streamKey.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IosCard,
        title = { Text("Подключение Платформы", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = IosLabelPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Выберите стриминговый сервис:",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosLabelSecondary
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = platform.defaultName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Платформа", color = IosLabelSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IosBlue,
                            unfocusedBorderColor = IosGlassBorder,
                            focusedContainerColor = IosCardElevated,
                            unfocusedContainerColor = IosCardElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Platform.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.defaultName) },
                                onClick = {
                                    platform = p
                                    name = p.defaultName
                                    rtmpUrl = p.defaultRtmpUrl
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя профиля", color = IosLabelSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosGlassBorder,
                        focusedContainerColor = IosCardElevated,
                        unfocusedContainerColor = IosCardElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP URL (напр. rtmp://live.twitch.tv/app)", color = IosLabelSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosGlassBorder,
                        focusedContainerColor = IosCardElevated,
                        unfocusedContainerColor = IosCardElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Ключ трансляции (Stream Key)", color = IosLabelSecondary) },
                    placeholder = { Text("Вставьте ключ из панели стримера...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosGlassBorder,
                        focusedContainerColor = IosCardElevated,
                        unfocusedContainerColor = IosCardElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(platform, name, rtmpUrl, streamKey) },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Сохранить", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = IosLabelSecondary)
            }
        }
    )
}
