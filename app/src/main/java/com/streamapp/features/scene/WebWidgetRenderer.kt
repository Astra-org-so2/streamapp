package com.streamapp.features.scene

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.streamapp.core.database.entity.SceneLayerEntity
import com.streamapp.core.designsystem.theme.IosGreen
import com.streamapp.core.designsystem.theme.IosPurple

/**
 * High-Visibility, Professional Web Widget Renderer with Real WebView & Rich Fallback UI
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebWidgetLiveRenderer(
    layer: SceneLayerEntity,
    modifier: Modifier = Modifier
) {
    val isRealUrl = layer.content.isNotBlank() && 
                    layer.content.startsWith("http", ignoreCase = true) && 
                    !layer.content.endsWith("token=", ignoreCase = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xD912141D)) // High-contrast translucent dark backing to prevent invisible/transparent widgets
            .border(1.dp, Color(0xFF0070F3).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        if (isRealUrl) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {}
                        webChromeClient = object : WebChromeClient() {}
                        
                        loadUrl(layer.content)
                    }
                },
                update = { wv ->
                    if (wv.url != layer.content) {
                        wv.loadUrl(layer.content)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        } else {
            // High-Fidelity Animated Previews for Goal Bar, Alerts, Chat
            val isGoalBar = layer.name.contains("Goal", ignoreCase = true) || 
                            layer.name.contains("Сбор", ignoreCase = true) || 
                            layer.name.contains("Цель", ignoreCase = true) || 
                            layer.content.contains("goal", ignoreCase = true)

            val isAlert = layer.name.contains("Alert", ignoreCase = true) || 
                          layer.name.contains("Донат", ignoreCase = true) || 
                          layer.content.contains("alert", ignoreCase = true)

            val isChat = layer.name.contains("Chat", ignoreCase = true) || 
                         layer.content.contains("chat", ignoreCase = true)

            if (isGoalBar) {
                // Authentic DonationAlerts Animated Goal Bar
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (layer.name.isNotBlank()) layer.name else "🎯 СБОР: НОВЫЙ ПК",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "75 000 / 100 000 RUB",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFFF007A),
                                            Color(0xFF7928CA),
                                            Color(0xFF0070F3)
                                        )
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("75% СОБРАНО", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.7f))
                        Text("ОСТАЛОСЬ 156 ДНЕЙ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else if (isAlert) {
                // Authentic Donation Alert Banner
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFE91E63).copy(alpha = 0.95f),
                                    Color(0xFF9C27B0).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("🔔 TopGamer_2026: 500 ₽", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Text("«Удачи в катке, тащи стрим!»", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.9f))
                    }
                }
            } else if (isChat) {
                // Translucent Chat Widget
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🎮 StreamerBoy: GG WP!", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = IosPurple)
                    Text("🔥 Alex99: Какой битрейт сейчас?", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = IosGreen)
                    Text("💬 ProPlayer: Тащи топ-1!", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White)
                }
            } else {
                // Generic Web Widget
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐 ${layer.name}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E5FF))
                }
            }
        }
    }
}
