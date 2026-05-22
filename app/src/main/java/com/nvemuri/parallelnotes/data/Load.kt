package com.nvemuri.parallelnotes.data

import android.graphics.BitmapFactory
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.data.entities.ImageElement
import com.nvemuri.parallelnotes.data.entities.PenStroke
import com.nvemuri.parallelnotes.data.entities.PenStyle
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.data.entities.SerializableElement
import com.nvemuri.parallelnotes.data.entities.ImportantStrokeEntity
import com.nvemuri.parallelnotes.data.entities.ImportantCategoryEntity
import com.nvemuri.parallelnotes.utils.bezierSmoothStroke
import com.nvemuri.parallelnotes.utils.createImagePicture
import kotlinx.serialization.json.Json
import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Database(entities = [NoteEntity::class, ImportantStrokeEntity::class, ImportantCategoryEntity::class], version = 5, exportSchema = false)
@TypeConverters(CanvasDataConverter::class) 
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun importantStrokeDao(): ImportantStrokeDao
    abstract fun importantCategoryDao(): ImportantCategoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT -16777216")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop and recreate important_strokes with new schema
                db.execSQL("DROP TABLE IF EXISTS `important_strokes` ")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `important_strokes` (
                        `id` TEXT NOT NULL, 
                        `folderName` TEXT NOT NULL, 
                        `noteId` TEXT NOT NULL, 
                        `noteTitle` TEXT NOT NULL, 
                        `serializedElements` TEXT NOT NULL, 
                        `minX` REAL NOT NULL, 
                        `maxX` REAL NOT NULL, 
                        `minY` REAL NOT NULL, 
                        `maxY` REAL NOT NULL, 
                        `colorArgb` INTEGER NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `important_categories` (
                        `name` TEXT NOT NULL, 
                        `colorArgb` INTEGER NOT NULL, 
                        PRIMARY KEY(`name`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parallel_notes_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
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
    val folder: String = "MyFolder",
    val colorArgb: Int = -16777216 // Default black (-16777216 = 0xFF000000)
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

fun SerializableElement.toCanvasElement(context: Context): CanvasElement {
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

            val strokePenStyle = runCatching { PenStyle.valueOf(penStyle) }.getOrDefault(PenStyle.SOLID)

            val nativePaint = NativePaint().apply {
                color = colorArgb
                isAntiAlias = true
                style = NativePaint.Style.STROKE
                strokeJoin = NativePaint.Join.ROUND
            }

            when (strokePenStyle) {
                PenStyle.SOLID -> {
                    nativePaint.strokeCap = NativePaint.Cap.ROUND
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
                }
                PenStyle.DASHED -> {
                    val avgPressure = smoothedPoints.map { 0.2f + it.pressure * 0.8f }.average().toFloat()
                    nativePaint.strokeWidth = avgPressure * thickness
                    nativePaint.strokeCap = NativePaint.Cap.ROUND
                    nativePaint.pathEffect = android.graphics.DashPathEffect(
                        floatArrayOf(thickness * 1.5f, thickness * 1f), 0f
                    )
                    val path = android.graphics.Path().apply {
                        moveTo(smoothedPoints.first().offset.x - minX, smoothedPoints.first().offset.y - minY)
                        for (i in 1 until smoothedPoints.size) {
                            lineTo(smoothedPoints[i].offset.x - minX, smoothedPoints[i].offset.y - minY)
                        }
                    }
                    nativeCanvas.drawPath(path, nativePaint)
                }
                PenStyle.HIGHLIGHTER -> {
                    nativePaint.alpha = 100
                    nativePaint.strokeWidth = thickness * 1.5f
                    nativePaint.strokeCap = NativePaint.Cap.SQUARE
                    if (smoothedPoints.size == 1) {
                        val p = smoothedPoints.first()
                        nativeCanvas.drawPoint(p.offset.x - minX, p.offset.y - minY, nativePaint)
                    } else {
                        val path = android.graphics.Path().apply {
                            moveTo(smoothedPoints.first().offset.x - minX, smoothedPoints.first().offset.y - minY)
                            for (i in 1 until smoothedPoints.size) {
                                lineTo(smoothedPoints[i].offset.x - minX, smoothedPoints[i].offset.y - minY)
                            }
                        }
                        nativeCanvas.drawPath(path, nativePaint)
                    }
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
                penStyle = strokePenStyle,
                picture = picture,
                minX = minX, maxX = maxX, minY = minY, maxY = maxY
            )
        }
        "IMAGE" -> {
            val path = requireNotNull(imagePath) { "ImageElement missing imagePath" }
            val file = File(context.filesDir, path)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val w = (maxX - minX).coerceAtLeast(1f)
            val h = (maxY - minY).coerceAtLeast(1f)
            val picture = if (bitmap != null) createImagePicture(bitmap, w, h) else Picture()
            ImageElement(
                id = id,
                zIndex = zIndex,
                imagePath = path,
                minX = minX, minY = minY,
                displayWidth = w, displayHeight = h,
                picture = picture
            )
        }
        "TEXT" -> {
            val textContent = requireNotNull(text) { "TextElement missing text content" }
            val w = displayWidth ?: (maxX - minX).coerceAtLeast(100f)
            val h = displayHeight ?: (maxY - minY).coerceAtLeast(50f)
            com.nvemuri.parallelnotes.data.entities.TextElement(
                id = id,
                zIndex = zIndex,
                text = textContent,
                minX = minX, minY = minY,
                displayWidth = w, displayHeight = h,
                picture = Picture()
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
