package com.streamapp.core.features.translation

import kotlinx.coroutines.tasks.await

import javax.inject.Inject
import javax.inject.Singleton
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

@Singleton
class ChatTranslator @Inject constructor() {

    private var translatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.SPANISH)
        .build()

    private var translator = Translation.getClient(translatorOptions)

    fun setLanguages(source: String, target: String) {
        translatorOptions = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        translator = Translation.getClient(translatorOptions)
    }

    suspend fun translateMessage(text: String): String? {
        return try {
            translator.downloadModelIfNeeded().await()
            translator.translate(text).await()
        } catch (e: Exception) {
            null
        }
    }
    
    fun close() {
        translator.close()
    }
}
