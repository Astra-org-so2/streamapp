package com.streamapp.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Authentic iOS System Dark Palette (Apple HIG)
val IosBackground = Color(0xFF000000)
val IosGroupedBackground = Color(0xFF000000)
val IosCard = Color(0xFF1C1C1E)
val IosCardElevated = Color(0xFF2C2C2E)
val IosCardTertiary = Color(0xFF3A3A3C)
val IosGlassBorder = Color(0x1FFFFFFF)
val IosSeparator = Color(0x38545458)

// Apple System Accent Colors
val IosBlue = Color(0xFF0A84FF)
val IosIndigo = Color(0xFF5E5CE6)
val IosPurple = Color(0xFFBF5AF2)
val IosPink = Color(0xFFFF375F)
val IosRed = Color(0xFFFF453A)
val IosOrange = Color(0xFFFF9F0A)
val IosYellow = Color(0xFFFFD60A)
val IosGreen = Color(0xFF30D158)
val IosMint = Color(0xFF63E6E2)
val IosTeal = Color(0xFF64D2FF)
val IosCyan = Color(0xFF70D7FF)

// Apple Typography
val IosLabelPrimary = Color(0xFFFFFFFF)
val IosLabelSecondary = Color(0xFF8E8E93)
val IosLabelTertiary = Color(0xFF48484A)
val IosLabelQuaternary = Color(0xFF2C2C2E)

// Gradients
val IosBlueGradient = Brush.horizontalGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))
val IosPurpleGradient = Brush.horizontalGradient(listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)))
val IosLiveRedGradient = Brush.horizontalGradient(listOf(Color(0xFFFF375F), Color(0xFFFF453A)))
val IosGreenGradient = Brush.horizontalGradient(listOf(Color(0xFF30D158), Color(0xFF34C759)))
val IosGlassGradient = Brush.verticalGradient(
    listOf(
        Color(0x33FFFFFF),
        Color(0x05FFFFFF)
    )
)

// Backward compatibility mappings
val BackgroundDark = IosBackground
val BackgroundDarkElevated = IosCard
val SurfaceDark = IosCard
val SurfaceVariantDark = IosCardElevated
val SurfaceBorderDark = IosGlassBorder
val AccentCyan = IosBlue
val AccentCyanGlow = Color(0x330A84FF)
val AccentViolet = IosPurple
val AccentVioletGlow = Color(0x33BF5AF2)
val StatusOnline = IosGreen
val StatusOnlineGlow = Color(0x3330D158)
val StatusWarning = IosOrange
val StatusWarningGlow = Color(0x33FF9F0A)
val StatusError = IosRed
val StatusErrorGlow = Color(0x33FF453A)
val StatusOffline = IosLabelSecondary
val TextPrimary = IosLabelPrimary
val TextSecondary = IosLabelSecondary
val TextTertiary = IosLabelTertiary
val TextDisabled = IosLabelQuaternary
val GlassBackground = Color(0xDE1C1C1E)
val GlassBorder = IosGlassBorder
