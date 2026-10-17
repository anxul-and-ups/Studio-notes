package com.example.data.repository

import com.example.data.db.NoteDao
import com.example.data.model.AutoClassifier
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allActiveNotes: Flow<List<NoteEntity>> = noteDao.getAllActiveNotes()
    val favoriteNotes: Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotes()
    val hiddenNotes: Flow<List<NoteEntity>> = noteDao.getHiddenNotes()

    fun getNotesByCategory(category: String): Flow<List<NoteEntity>> =
        noteDao.getNotesByCategory(category)

    fun searchNotes(query: String): Flow<List<NoteEntity>> =
        noteDao.searchNotes(query)

    fun getNoteById(id: Long): Flow<NoteEntity?> =
        noteDao.getNoteById(id)

    suspend fun getNoteByIdOnce(id: Long): NoteEntity? =
        noteDao.getNoteByIdOnce(id)

    suspend fun saveNote(note: NoteEntity): Long =
        noteDao.insertNote(note)

    suspend fun saveNote(
        id: Long = 0,
        title: String,
        content: String,
        manualCategory: String? = null,
        isFavorite: Boolean = false,
        isLocked: Boolean = false,
        fontSize: Int = 16,
        fontColorHex: String = "#FFFFFF",
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        isStrikethrough: Boolean = false,
        isCodeFormat: Boolean = false,
        alignment: String = "left",
        listType: String = "none",
        tableData: String = "",
        attachmentUri: String = ""
    ): Long {
        val detectedCat = manualCategory ?: AutoClassifier.detectCategory(title, content)
        val targetFolder = when (detectedCat) {
            "API" -> "APIs Keys"
            "Code" -> "Code"
            "Media" -> "Media"
            "Personal" -> "Personal"
            else -> "All Notes"
        }

        val note = NoteEntity(
            id = id,
            title = if (title.isBlank()) "Untitled Note" else title.trim(),
            content = content,
            category = detectedCat,
            folder = targetFolder,
            isFavorite = isFavorite,
            isLocked = isLocked,
            isTrash = false,
            updatedAt = System.currentTimeMillis(),
            fontSize = fontSize,
            fontColorHex = fontColorHex,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            isStrikethrough = isStrikethrough,
            isCodeFormat = isCodeFormat || detectedCat == "Code",
            alignment = alignment,
            listType = listType,
            tableData = tableData,
            attachmentUri = attachmentUri
        )

        return if (id == 0L) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
            id
        }
    }

    suspend fun moveToTrash(id: Long) = noteDao.moveToTrash(id)

    suspend fun restoreFromTrash(id: Long) = noteDao.restoreFromTrash(id)

    suspend fun permanentDelete(id: Long) = noteDao.permanentDelete(id)

    suspend fun emptyTrash() = noteDao.emptyTrash()

    suspend fun toggleFavorite(id: Long, currentStatus: Boolean) =
        noteDao.updateFavorite(id, !currentStatus)

    suspend fun toggleLock(id: Long, currentStatus: Boolean) =
        noteDao.updateLocked(id, !currentStatus)

    suspend fun togglePin(id: Long, currentStatus: Boolean) =
        noteDao.updatePinned(id, !currentStatus)

    // ---- Hidden notes vault (Security Area) ----
    suspend fun hideNotes(notes: List<NoteEntity>) {
        notes.forEach { noteDao.updateHidden(it.id, true, it.folder) }
    }

    suspend fun unhideNote(note: NoteEntity) {
        noteDao.updateHidden(note.id, false, note.originalFolder.ifBlank { "All Notes" })
    }

    // ---- Batch helpers (multi-select delete / restore) ----
    suspend fun moveToTrash(ids: Collection<Long>) = ids.forEach { noteDao.moveToTrash(it) }

    suspend fun restoreFromTrash(ids: Collection<Long>) = ids.forEach { noteDao.restoreFromTrash(it) }

    suspend fun permanentDelete(ids: Collection<Long>) = ids.forEach { noteDao.permanentDelete(it) }
}
