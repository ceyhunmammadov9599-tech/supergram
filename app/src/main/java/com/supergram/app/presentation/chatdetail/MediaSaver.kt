package com.supergram.app.presentation.chatdetail

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves a downloaded media file into the system gallery via MediaStore.
 * On Android 10+ (API 29+) this requires no storage permissions at all.
 */
object MediaSaver {

    /** @return null on success, or a human-readable failure reason. */
    fun saveToGallery(context: Context, localPath: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "Saving requires Android 10+"
        }
        val source = File(localPath)
        if (!source.exists()) return "File not found"

        val extension = source.extension.takeIf { it.isNotBlank() } ?: "jpg"
        val mimeType = when (extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SuperGram_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SuperGram")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return "Gallery unavailable"

        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return "Gallery unavailable"
            null
        } catch (e: Exception) {
            "Save failed"
        }
    }
}
