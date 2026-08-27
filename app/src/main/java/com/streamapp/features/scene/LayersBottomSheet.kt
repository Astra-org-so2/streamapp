package com.streamapp.features.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.theme.AccentCyan
import com.streamapp.core.designsystem.theme.SurfaceDark
import com.streamapp.core.designsystem.theme.TextPrimary
import com.streamapp.core.designsystem.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    sceneName: String,
    layers: List<SceneLayerEntity>,
    onDismiss: () -> Unit,
    onAddLayerClick: () -> Unit,
    onToggleVisibility: (layerId: String, currentVisible: Boolean) -> Unit,
    onDeleteLayer: (layerId: String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Scene Layers",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Active Scene: $sceneName",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCyan
                    )
                }

                Button(
                    onClick = onAddLayerClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Source", color = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (layers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No layers on this scene yet.\nAdd a browser source (Donations/Alerts), banner, or text!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                ) {
                    items(layers, key = { it.id }) { layer ->
                        LayerItemRow(
                            layer = layer,
                            onToggleVisibility = { onToggleVisibility(layer.id, layer.isVisible) },
                            onDelete = { onDeleteLayer(layer.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LayerItemRow(
    layer: SceneLayerEntity,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (layer.type) {
                LayerType.WEB -> Icons.Default.Language
                LayerType.TEXT -> Icons.Default.TextFields
                LayerType.IMAGE -> Icons.Default.Image
                LayerType.CAMERA_PIP -> Icons.Default.Videocam
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (layer.isVisible) AccentCyan else TextSecondary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (layer.isVisible) TextPrimary else TextSecondary
                )
                Text(
                    text = layer.content.take(35) + if (layer.content.length > 35) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Visibility",
                    tint = if (layer.isVisible) AccentCyan else TextSecondary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Layer",
                    tint = Color(0xFFFF5252)
                )
            }
        }
    }
}
