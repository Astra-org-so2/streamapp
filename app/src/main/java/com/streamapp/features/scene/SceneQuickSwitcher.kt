package com.streamapp.features.scene

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.streamapp.core.database.entity.SceneEntity
import com.streamapp.core.designsystem.theme.AccentCyan
import com.streamapp.core.designsystem.theme.BackgroundDark
import com.streamapp.core.designsystem.theme.SurfaceDark
import com.streamapp.core.designsystem.theme.TextPrimary
import com.streamapp.core.designsystem.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneQuickSwitcher(
    scenes: List<SceneEntity>,
    activeScene: SceneEntity?,
    onSelectScene: (sceneId: String) -> Unit,
    onCreateScene: (name: String) -> Unit,
    onDeleteScene: (sceneId: String) -> Unit = {},
    onOpenLayersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateSceneDialog by remember { mutableStateOf(false) }
    var sceneToDelete by remember { mutableStateOf<SceneEntity?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenLayersClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Manage Layers",
                tint = AccentCyan
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            scenes.forEach { scene ->
                val isSelected = scene.id == activeScene?.id
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) AccentCyan else SurfaceDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onSelectScene(scene.id) },
                            onLongClick = {
                                if (scenes.size > 1) {
                                    sceneToDelete = scene
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scene.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) BackgroundDark else TextPrimary
                        )
                        if (isSelected && scenes.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Scene",
                                tint = BackgroundDark,
                                modifier = Modifier
                                    .size(14.dp)
                                    .combinedClickable(
                                        onClick = { sceneToDelete = scene }
                                    )
                            )
                        }
                    }
                }
            }

            // Add Scene Chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                onClick = { showCreateSceneDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Scene",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    // Delete Scene Confirmation Dialog
    sceneToDelete?.let { scene ->
        AlertDialog(
            onDismissRequest = { sceneToDelete = null },
            title = { Text("Удалить сцену?") },
            text = { Text("Вы уверены, что хотите удалить сцену «${scene.name}» со всеми её виджетами?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteScene(scene.id)
                        sceneToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sceneToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showCreateSceneDialog) {
        var sceneName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateSceneDialog = false },
            title = { Text("Создать новую сцену") },
            text = {
                OutlinedTextField(
                    value = sceneName,
                    onValueChange = { sceneName = it },
                    label = { Text("Название (напр. Игра, Чат, Перерыв)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sceneName.isNotBlank()) {
                            onCreateScene(sceneName.trim())
                            showCreateSceneDialog = false
                        }
                    },
                    enabled = sceneName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Создать", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSceneDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
