package com.nvemuri.parallelnotes.data

import androidx.room.*
import com.nvemuri.parallelnotes.data.entities.ImportantStrokeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportantStrokeDao {
    @Query("SELECT * FROM important_strokes WHERE folderName = :folderName ORDER BY timestamp DESC")
    fun getImportantStrokesForFolder(folderName: String): Flow<List<ImportantStrokeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stroke: ImportantStrokeEntity)

    @Delete
    suspend fun delete(stroke: ImportantStrokeEntity)
    
    @Query("SELECT * FROM important_strokes WHERE folderName = :folderName")
    suspend fun getImportantStrokesForFolderSync(folderName: String): List<ImportantStrokeEntity>
}
