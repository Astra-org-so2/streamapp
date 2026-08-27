package com.streamapp.core.model

enum class DestinationConnectionState {
    DISABLED,    // Отключен пользователем
    READY,       // Готов к трансляции (Включен)
    CONNECTING,  // Идет подключение
    LIVE,        // В эфире
    ERROR        // Ошибка вещания
}

data class BroadcastDestination(
    val id: String,
    val platform: Platform,
    val name: String,
    val rtmpUrl: String,
    val streamKey: String,
    val isEnabled: Boolean,
    val connectionState: DestinationConnectionState = DestinationConnectionState.DISABLED
)

object DestinationValidator {
    /**
     * Validates scheme (rtmp:// or rtmps://), host presence, and strips trailing slashes.
     * Returns sanitized URL or null if invalid.
     */
    fun validateAndNormalizeRtmpUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("rtmp://", ignoreCase = true) && !trimmed.startsWith("rtmps://", ignoreCase = true)) {
            return null
        }
        val schemePrefix = if (trimmed.startsWith("rtmps://", ignoreCase = true)) "rtmps://" else "rtmp://"
        val afterScheme = trimmed.substring(schemePrefix.length)
        if (afterScheme.isBlank() || afterScheme.startsWith("/")) {
            return null
        }
        if (trimmed.contains(Regex("\\s"))) {
            return null
        }
        return trimmed.trimEnd('/')
    }

    /**
     * Trims whitespace, removes newlines/tabs, ensures length and characters are valid.
     */
    fun sanitizeStreamKey(rawKey: String): String? {
        val cleaned = rawKey.trim().replace(Regex("[\\s\r\n\t]+"), "")
        if (cleaned.isBlank() || cleaned.length < 3) return null
        return cleaned
    }
}
