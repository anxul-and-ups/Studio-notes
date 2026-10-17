package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "General", // "General", "Code", "API", "Media", "Personal"
    val folder: String = "All Notes",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val originalFolder: String = "All Notes",
    val isTrash: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val fontSize: Int = 16,
    val fontColorHex: String = "#FFFFFF",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isCodeFormat: Boolean = false,
    val alignment: String = "left", // "left", "center", "right"
    val listType: String = "none",  // "none", "bullet", "digit", "letter"
    val tableData: String = "",     // Markdown or JSON table
    val attachmentUri: String = "",
    // JSON array of independent per-character style spans (bold/italic/underline/
    // strikethrough/code/color/highlight/fontSize). Formatting is NEVER embedded
    // into `content` as raw markup — it always lives here, separately, so applying
    // a style only ever affects the selected range, and the note body never shows
    // broken tags like "**" or "<u>". See RichTextFormatter.kt.
    val styleSpansJson: String = "[]",
    // Serialized list of attachments (images, PDFs, docs, code/text files) actually
    // linked to this note. See AttachmentInfo in RichTextFormatter/NoteRepository usage.
    val attachmentsJson: String = "[]"
)
