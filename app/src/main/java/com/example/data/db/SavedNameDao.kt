package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SavedNameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedNameDao {
    @Query("SELECT * FROM saved_names ORDER BY savedAt DESC")
    fun getAllSavedNames(): Flow<List<SavedNameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertName(name: SavedNameEntity): Long

    @Delete
    suspend fun deleteName(name: SavedNameEntity)

    @Query("DELETE FROM saved_names WHERE id = :id")
    suspend fun deleteById(id: Long)
}
