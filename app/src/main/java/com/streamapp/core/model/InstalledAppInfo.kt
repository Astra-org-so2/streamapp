package com.streamapp.core.model

import android.graphics.drawable.Drawable

enum class ScreenCaptureMode {
    ENTIRE_SCREEN,
    SPECIFIC_APP
}

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)
