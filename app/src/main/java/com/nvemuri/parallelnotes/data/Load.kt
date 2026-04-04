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
import com.nvemuri.parallelnotes.utils.bezierSmoothStroke
import kotlinx.serialization.json.Json
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Database(entities = [NoteEntity::class], version = 2, exportSchema = false)
@TypeConverters(CanvasDataConverter::class) 
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parallel_notes_database"
                )
                    .fallbackToDestructiveMigration() // Note: This will wipe data on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT folder FROM notes")
    fun getAllFolders(): Flow<List<String>>

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
    val lastModified: Long = System.currentTimeMillis(),
    val folder: String = "MyFolder" // Default folder for new/existing notes
)

class CanvasDataConverter {
    @TypeConverter
    fun fromCanvasData(data: List<SerializableElement>): String = Json.encodeToString(data)
    @TypeConverter
    fun toCanvasData(jsonString: String): List<SerializableElement> {
        if (jsonString.isBlank()) return emptyList()
        return Json.decodeFromString(jsonString)
    }
}

fun SerializableElement.toCanvasElement(): CanvasElement {
    return when (type) {
        "PEN" -> {
            val composeColor = Color(colorArgb)
            val savedPoints = points.map { Point(Offset(it.x, it.y), it.pressure) }
            
            val smoothedPoints = if (version >= 1 && arcSmoothing) {
                bezierSmoothStroke(savedPoints)
            } else {
                savedPoints
            }

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

            if (smoothedPoints.size == 1) {
                val p = smoothedPoints.first()
                nativePaint.strokeWidth = (0.2f + (p.pressure * 0.8f)) * thickness
                nativeCanvas.drawPoint(p.offset.x - minX, p.offset.y - minY, nativePaint)
            } else {
                for (i in 0 until smoothedPoints.size - 1) {
                    val start = smoothedPoints[i]
                    val end = smoothedPoints[i + 1]
                    nativePaint.strokeWidth = (0.2f + (end.pressure * 0.8f)) * thickness
                    nativeCanvas.drawLine(
                        start.offset.x - minX, start.offset.y - minY,
                        end.offset.x - minX, end.offset.y - minY,
                        nativePaint
                    )
                }
            }
            picture.endRecording()

            PenStroke(
                id = id,
                zIndex = zIndex,
                rawPoints = savedPoints,
                points = smoothedPoints,
                arcSmoothing = arcSmoothing,
                thickness = thickness,
                color = composeColor,
                picture = picture,
                minX = minX, maxX = maxX, minY = minY, maxY = maxY
            )
        }
        else -> throw IllegalArgumentException("Unknown type: $type")
    }
}

data class NoteSummary(
    val noteId: String,
    val title: String,
    val lastModified: Long
)
