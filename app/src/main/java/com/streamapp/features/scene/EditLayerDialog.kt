package com.streamapp.features.scene

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.util.FileStorageHelper

@Composable
fun EditLayerDialog(
    layer: SceneLayerEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var layerName by remember { mutableStateOf(layer.name) }
    var content by remember { mutableStateOf(layer.content) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val localPath = FileStorageHelper.copyUriToInternalStorage(context, it, "images")
            if (localPath != null) {
                content = localPath
            }
        }
    }

    val isFormValid = layerName.isNotBlank() && (layer.type == LayerType.CAMERA_PIP || content.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IosCard,
        title = {
            Text(
                "Настройка слоя: ${layer.name}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = IosLabelPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = layerName,
                    onValueChange = { layerName = it },
                    label = { Text("Название слоя", color = IosLabelSecondary) },
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

                if (layer.type == LayerType.IMAGE) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (content.isNotBlank()) "Сменить картинку" else "Выбрать картинку", color = Color.White)
                    }
                } else if (layer.type != LayerType.CAMERA_PIP) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = {
                            Text(
                                when (layer.type) {
                                    LayerType.WEB -> "Ссылка на виджет (DonationAlerts / Chat)"
                                    LayerType.TEXT -> "Текст плашки или титра"
                                    else -> "Контент"
                                },
                                color = IosLabelSecondary
                            )
                        },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFormValid) {
                        onSave(layerName, content)
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue)
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
