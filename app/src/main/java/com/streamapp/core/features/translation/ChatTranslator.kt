package com.streamapp.core.features.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatTranslator @Inject constructor() {

    private var currentSource = TranslateLanguage.ENGLISH
    private var currentTarget = TranslateLanguage.RUSSIAN

    private var translator: Translator? = null
    private val downloadedLanguageModels = ConcurrentHashMap.newKeySet<String>()
    private val translatorLock = Any()

    init {
        initTranslator(currentSource, currentTarget)
    }

    private fun initTranslator(source: String, target: String) {
        synchronized(translatorLock) {
            closeInternal()
            currentSource = source
            currentTarget = target

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()

            translator = Translation.getClient(options)
            AppLogger.i(LogCategory.CHAT, "ChatTranslator initialized: $source -> $target")
        }
    }

    fun setLanguages(source: String, target: String) {
        if (source == currentSource && target == currentTarget && translator != null) return
        initTranslator(source, target)
    }

    suspend fun translateMessage(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return text

        val activeTranslator = synchronized(translatorLock) { translator } ?: return null
        val pairKey = "$currentSource->$currentTarget"

        return try {
            if (!downloadedLanguageModels.contains(pairKey)) {
                AppLogger.i(LogCategory.CHAT, "Downloading translation model for $pairKey if needed...")
                activeTranslator.downloadModelIfNeeded().await()
                downloadedLanguageModels.add(pairKey)
            }
            activeTranslator.translate(trimmed).await()
        } catch (e: Exception) {
            AppLogger.w(LogCategory.CHAT, "Translation failed for message '$trimmed' ($pairKey): ${e.message}")
            null
        }
    }

    private fun closeInternal() {
        try {
            translator?.close()
        } catch (e: Exception) {
            AppLogger.w(LogCategory.CHAT, "Error closing translator: ${e.message}")
        } finally {
            translator = null
        }
    }

    fun close() {
        synchronized(translatorLock) {
            closeInternal()
            downloadedLanguageModels.clear()
            AppLogger.i(LogCategory.CHAT, "ChatTranslator closed")
        }
    }
}
