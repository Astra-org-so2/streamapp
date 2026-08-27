package com.streamapp.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileStorageHelper {

    private const val MIN_FREE_SPACE_BYTES = 50L * 1024 * 1024 // 50 MB safety threshold

    fun copyUriToInternalStorage(context: Context, uri: Uri, subDirName: String): String? {
        val cleanSubDir = subDirName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(context.filesDir, cleanSubDir).apply { if (!exists()) mkdirs() }

        if (dir.usableSpace < MIN_FREE_SPACE_BYTES) {
            AppLogger.e(LogCategory.STORAGE, "Insufficient disk space to copy file (available: ${dir.usableSpace} bytes)")
            return null
        }

        val rawFileName = getFileName(context, uri) ?: "file_${UUID.randomUUID()}"
        val sanitizedFileName = sanitizeFileName(rawFileName)
        val destFile = File(dir, "${System.currentTimeMillis()}_$sanitizedFileName")

        var success = false
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                AppLogger.e(LogCategory.STORAGE, "Failed to open input stream for URI: $uri")
                return null
            }

            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                success = true
                AppLogger.i(LogCategory.STORAGE, "Successfully copied ${destFile.length()} bytes to ${destFile.absolutePath}")
                destFile.absolutePath
            } else {
                AppLogger.e(LogCategory.STORAGE, "Destination file is empty after copy")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(LogCategory.STORAGE, "Error copying URI to internal storage", e)
            null
        } finally {
            if (!success && destFile.exists()) {
                destFile.delete()
            }
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.STORAGE, "Failed to query display name for URI: $uri")
            }
        }
        if (name == null) {
            name = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) it.substring(cut + 1) else it
            }
        }
        return name
    }

    fun sanitizeFileName(fileName: String): String {
        // Strip illegal filename characters and truncate length
        return fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(64)
    }
}
