package com.example.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * Copies a file picked via the system file/photo picker into this app's private
 * storage (files/attachments) so it keeps working even if the original picker URI
 * loses permission later, and returns real metadata (name, mime, size) for it.
 *
 * This is the actual attach step referenced by NoteEntity.attachmentsJson — it
 * never just inserts a placeholder string into the note text.
 */
object AttachmentStorage {

    fun copyToAppStorage(context: Context, sourceUri: Uri): RichTextFormatter.AttachmentInfo? {
        return try {
            val resolver = context.contentResolver
            val originalName = queryDisplayName(context, sourceUri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(sourceUri) ?: guessMimeFromName(originalName)

            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val safeName = "${UUID.randomUUID()}_${originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
            val destFile = File(dir, safeName)

            resolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val isImage = mimeType.startsWith("image/")
            var w = 0
            var h = 0
            if (isImage) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, o)
                w = o.outWidth.coerceAtLeast(0)
                h = o.outHeight.coerceAtLeast(0)
            }
            RichTextFormatter.AttachmentInfo(
                uri = destFile.absolutePath,
                fileName = originalName,
                mimeType = mimeType,
                sizeBytes = destFile.length(),
                srcWidth = w,
                srcHeight = h
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Turns a stored absolute file path (from copyToAppStorage) into a content:// Uri
     * via the app's FileProvider, safe to pass to another app in an Intent. */
    fun getShareableUri(context: Context, absolutePath: String): Uri {
        val file = File(absolutePath)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "md" -> "text/markdown"
            "doc", "docx" -> "application/msword"
            else -> "*/*"
        }
    }
}
