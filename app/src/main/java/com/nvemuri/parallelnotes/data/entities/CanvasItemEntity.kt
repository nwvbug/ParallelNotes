package com.nvemuri.parallelnotes.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ItemType { STROKE, TEXT, IMAGE }

@Entity(
    tableName = "canvas_items",
    // This links the item to a specific note. If the note is deleted, CASCADE deletes all its items.
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Indexing the noteId makes loading a note significantly faster
    indices = [Index("noteId")]
)
data class CanvasItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val type: ItemType,
    val zIndex: Int, // The order they were drawn (determines what overlaps what)

    // --- THE INFINITE CANVAS BOUNDING BOX ---
    // Instead of querying thousands of individual points, the database uses these
    // 4 numbers to instantly know if this item is currently visible on the screen.
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,

    // --- THE PAYLOAD ---
    // If it's a STROKE: A JSON string of your X,Y coordinates.
    // If it's TEXT: The actual written text.
    // If it's an IMAGE: The local file path (e.g., "/data/user/0/.../image1.png")
    val payload: String,

    // --- THE STYLING ---
    val colorArgb: Long, // Compose Colors can be converted to Longs for storage
    val thickness: Float,

    // --- SYNCING ---
    val lastModified: Long = System.currentTimeMillis()
)