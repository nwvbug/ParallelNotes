package com.nvemuri.parallelnotes.data
import androidx.room.*
import com.nvemuri.parallelnotes.data.entities.CanvasItemEntity
import com.nvemuri.parallelnotes.data.entities.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ==========================================
    // NOTE OPERATIONS (For the Homepage)
    // ==========================================

    // Room automatically generates the SQL: INSERT INTO notes ...
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    // Flow:
    // It creates an active pipe to the database. If a note is added/deleted,
    // it automatically pushes the new list to your UI and triggers a recomposition.
    @Query("SELECT * FROM notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>


    // ==========================================
    // CANVAS ITEM OPERATIONS (For the Drawing Screen)
    // ==========================================

    // Save a batch of strokes all at once
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasItems(items: List<CanvasItemEntity>)

    // Load everything for a specific note (Good for standard canvases)
    // We order by zIndex so the ink draws back-to-front correctly!
    @Query("SELECT * FROM canvas_items WHERE noteId = :noteId ORDER BY zIndex ASC")
    suspend fun getAllCanvasItemsForNote(noteId: String): List<CanvasItemEntity>

    // --- THE INFINITE CANVAS SPATIAL QUERY ---
    // This is standard Bounding-Box collision math in SQL.
    // It only returns ink that is physically visible inside the user's screen.
    @Query("""
        SELECT * FROM canvas_items 
        WHERE noteId = :noteId 
        AND minX <= :viewportMaxX AND maxX >= :viewportMinX 
        AND minY <= :viewportMaxY AND maxY >= :viewportMinY 
        ORDER BY zIndex ASC
    """)
    suspend fun getVisibleCanvasItems(
        noteId: String,
        viewportMinX: Float,
        viewportMaxX: Float,
        viewportMinY: Float,
        viewportMaxY: Float
    ): List<CanvasItemEntity>
}