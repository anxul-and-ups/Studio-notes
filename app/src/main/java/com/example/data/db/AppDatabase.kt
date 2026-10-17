package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.NoteEntity
import com.example.data.model.SavedNameEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [NoteEntity::class, SavedNameEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun savedNameDao(): SavedNameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return getDatabase(context, CoroutineScope(Dispatchers.IO))
        }

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "au_notes_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            populateInitialNotes(getDatabase(context, scope).noteDao())
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialNotes(dao: NoteDao) {
            val currentTime = System.currentTimeMillis()

            dao.insertNote(
                NoteEntity(
                    title = "Welcome to AU Notes",
                    content = """
Welcome to AU Notes — your smart, glassmorphism-powered workspace!

AU Notes is designed to combine elegant aesthetic design with high-productivity tools for notes, code snippets, API keys, and documents.

✨ Key Features:
• Rich Note Editor: Format text with bold, italic, custom colors, checklists, and interactive tables.
• Floating AU AI Assistant: Summarize documents, analyze code, and generate structured notes.
• Storage File Explorer: View PDFs, open text and code files, and save edits directly.
• Universal Export: Export your notes to PDF, DOCX, HTML, TXT, PY, and JSON with formatting alerts.
• Security Vault: Protect folders and private notes with a 4-digit PIN and security question recovery.

👨‍💻 Developer Note:
AU Notes was crafted by Anshul (@anxul_ydv), a student developer passionate about building sleek, functional, and modern Android applications.
                    """.trimIndent(),
                    category = "General",
                    folder = "All Notes",
                    isFavorite = true,
                    isPinned = true,
                    isLocked = false,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
            )
        }
    }
}
