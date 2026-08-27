package com.streamapp.features.scene

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.designsystem.components.IosCard
import com.streamapp.core.designsystem.components.IosSegmentedControl
import com.streamapp.core.designsystem.theme.*
import com.streamapp.core.model.WidgetCategory
import com.streamapp.core.model.WidgetTemplate
import com.streamapp.core.model.WidgetTemplatesCatalog
import com.streamapp.core.util.FileStorageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerDialog(
    onDismiss: () -> Unit,
    onAddLayer: (
        type: LayerType,
        name: String,
        content: String,
        width: Float,
        height: Float,
        alpha: Float
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(WidgetCategory.ALERTS_DONATIONS) }
    var selectedTemplate by remember { mutableStateOf<WidgetTemplate?>(WidgetTemplatesCatalog.templates.first()) }

    var layerName by remember { mutableStateOf(selectedTemplate?.defaultName ?: "Виджет") }
    var content by remember { mutableStateOf(selectedTemplate?.defaultContent ?: "") }
    var layerWidth by remember { mutableStateOf(selectedTemplate?.defaultWidth ?: 0.8f) }
    var layerHeight by remember { mutableStateOf(selectedTemplate?.defaultHeight ?: 0.25f) }
    var layerAlpha by remember { mutableStateOf(selectedTemplate?.defaultAlpha ?: 1.0f) }

    val currentType = selectedTemplate?.type ?: LayerType.WEB
    val isFormValid = layerName.isNotBlank() && (currentType == LayerType.CAMERA_PIP || content.isNotBlank())

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val localPath = FileStorageHelper.copyUriToInternalStorage(context, it, "images")
            if (localPath != null) {
                content = localPath
                if (layerName == "Sponsor Banner" || layerName == "Banner / Frame" || layerName == "Widget") {
                    layerName = "Custom Image"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IosCard,
        title = {
            Text(
                "Галерея Виджетов и Оверлеев",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = IosLabelPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Categories
                IosSegmentedControl(
                    items = WidgetCategory.entries,
                    selectedItem = selectedCategory,
                    onItemSelected = { selectedCategory = it },
                    itemLabel = {
                        when (it) {
                            WidgetCategory.ALERTS_DONATIONS -> "Алерты"
                            WidgetCategory.CHAT_INTERACTION -> "Чат"
                            WidgetCategory.SOCIALS_TEXT -> "Текст"
                            WidgetCategory.OVERLAYS_FRAMES -> "Баннеры"
                        }
                    }
                )

                // Templates
                val categoryTemplates = WidgetTemplatesCatalog.templates.filter { it.category == selectedCategory }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryTemplates.forEach { tmpl ->
                        val isSelected = tmpl.id == selectedTemplate?.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) IosBlue.copy(alpha = 0.15f) else IosCardElevated,
                            border = if (isSelected) BorderStroke(1.dp, IosBlue) else BorderStroke(0.5.dp, IosGlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplate = tmpl
                                    layerName = tmpl.defaultName
                                    content = tmpl.defaultContent
                                    layerWidth = tmpl.defaultWidth
                                    layerHeight = tmpl.defaultHeight
                                    layerAlpha = tmpl.defaultAlpha
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (tmpl.type) {
                                    LayerType.WEB -> Icons.Default.Language
                                    LayerType.TEXT -> Icons.Default.TextFields
                                    LayerType.IMAGE -> Icons.Default.Image
                                    LayerType.CAMERA_PIP -> Icons.Default.Videocam
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) IosBlue else IosLabelSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tmpl.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) IosBlue else IosLabelPrimary
                                    )
                                    Text(
                                        text = tmpl.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IosLabelSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = IosGlassBorder)

                Text("Настройка Виджета", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = IosBlue)

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

                if (currentType == LayerType.IMAGE) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (content.isNotBlank()) "Картинка загружена (Сменить)" else "Выбрать из галереи", color = Color.White)
                    }
                } else if (currentType != LayerType.CAMERA_PIP) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(selectedTemplate?.placeholder ?: "Ссылка или текст", color = IosLabelSecondary) },
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
                        onAddLayer(currentType, layerName, content, layerWidth, layerHeight, layerAlpha)
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue)
            ) {
                Text("Добавить на стрим", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = IosLabelSecondary)
            }
        }
    )
}
