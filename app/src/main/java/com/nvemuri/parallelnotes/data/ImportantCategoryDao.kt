package com.nvemuri.parallelnotes.data

import androidx.room.*
import com.nvemuri.parallelnotes.data.entities.ImportantCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportantCategoryDao {
    @Query("SELECT * FROM important_categories")
    fun getAllCategories(): Flow<List<ImportantCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(category: ImportantCategoryEntity)

    @Delete
    suspend fun delete(category: ImportantCategoryEntity)
    
    @Query("SELECT * FROM important_categories")
    suspend fun getAllCategoriesSync(): List<ImportantCategoryEntity>
}
