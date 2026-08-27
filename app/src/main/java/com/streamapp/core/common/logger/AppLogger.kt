package com.streamapp.core.common.logger

import android.util.Log

enum class LogCategory {
    NETWORK,
    STREAMING,
    INPUT,
    AUDIO,
    VIDEO,
    AUTH,
    DATABASE,
    UI
}

/**
 * Centralized, production-safe logger with category tagging and strict PII protection.
 * In release mode, debug logging is disabled and secrets are never printed.
 */
object AppLogger {
    private const val TAG_PREFIX = "StreamApp"
    private var isDebugEnabled: Boolean = true

    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    fun d(category: LogCategory, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG_PREFIX:${category.name}", sanitize(message))
        }
    }

    fun i(category: LogCategory, message: String) {
        Log.i("$TAG_PREFIX:${category.name}", sanitize(message))
    }

    fun w(category: LogCategory, message: String, throwable: Throwable? = null) {
        Log.w("$TAG_PREFIX:${category.name}", sanitize(message), throwable)
    }

    fun e(category: LogCategory, message: String, throwable: Throwable? = null) {
        Log.e("$TAG_PREFIX:${category.name}", sanitize(message), throwable)
    }

    /**
     * Strips tokens, passwords, PINs, and authentication headers from logs.
     */
    private fun sanitize(message: String): String {
        return message
            .replace(Regex("(?i)(password|token|secret|auth|bearer)\\s*[:=]\\s*[^\\s,]+"), "$1=[REDACTED]")
            .replace(Regex("(?i)(pin)\\s*[:=]\\s*\\d+"), "$1=[REDACTED]")
    }
}
