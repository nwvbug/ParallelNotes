package com.nvemuri.parallelnotes.data

import android.graphics.Picture
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.data.entities.PenStroke
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.data.entities.SerializableElement
import kotlinx.serialization.json.Json
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
@TypeConverters(CanvasDataConverter::class) // Attach the JSON converter!
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile // Ensures changes to INSTANCE are immediately visible to all threads
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If INSTANCE is not null, return it. Otherwise, create the database.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parallel_notes_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>> // Back to returning the standard Entity!

    @Query("SELECT * FROM notes WHERE noteId = :id")
    suspend fun getNoteById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val noteId: String,
    val title: String,
    val lastModified: Long = System.currentTimeMillis()
    // ❌ We completely removed the canvasData list and Converter!
)

class CanvasDataConverter {

    @TypeConverter
    fun fromCanvasData(data: List<SerializableElement>): String {
        // Converts the List into a JSON String
        return Json.encodeToString(data)
    }

    @TypeConverter
    fun toCanvasData(jsonString: String): List<SerializableElement> {
        // Converts the JSON String back into a List
        // If the string is empty, return an empty list to prevent crashes
        if (jsonString.isBlank()) return emptyList()
        return Json.decodeFromString(jsonString)
    }
}

fun SerializableElement.toCanvasElement(): CanvasElement {
    return when (type) {
        "PEN" -> {
            val composeColor = Color(colorArgb)

            // 1. Recreate the Picture
            val picture = Picture()
            val width = (maxX - minX).toInt() + 1
            val height = (maxY - minY).toInt() + 1
            val nativeCanvas: NativeCanvas = picture.beginRecording(width, height)

            val nativePaint = NativePaint().apply {
                color = colorArgb
                isAntiAlias = true
                strokeCap = NativePaint.Cap.ROUND
                strokeJoin = NativePaint.Join.ROUND
            }

            // 2. Redraw the math into the new Picture
            if (points.size == 1) {
                val p = points.first()
                nativePaint.strokeWidth = (0.2f + (p.pressure * 0.8f)) * thickness
                nativeCanvas.drawPoint(p.x - minX, p.y - minY, nativePaint)
            } else {
                for (i in 0 until points.size - 1) {
                    val start = points[i]
                    val end = points[i + 1]
                    nativePaint.strokeWidth = (0.2f + (end.pressure * 0.8f)) * thickness
                    nativeCanvas.drawLine(
                        start.x - minX, start.y - minY,
                        end.x - minX, end.y - minY,
                        nativePaint
                    )
                }
            }
            picture.endRecording()

            // 3. Return the fully reconstructed Runtime object
            PenStroke(
                id = id,
                zIndex = zIndex,
                points = points.map { Point(Offset(it.x, it.y), it.pressure) },
                thickness = thickness,
                color = composeColor,
                picture = picture,
                minX = minX, maxX = maxX, minY = minY, maxY = maxY
            )
        }
        else -> throw IllegalArgumentException("Unknown type: $type")
    }
}

// Put this near NoteEntity
data class NoteSummary(
    val noteId: String,
    val title: String,
    val lastModified: Long
)