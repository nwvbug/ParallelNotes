package com.nvemuri.parallelnotes.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "important_strokes")
data class ImportantStrokeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val folderName: String,
    val noteId: String,
    val noteTitle: String,
    val serializedElements: List<SerializableElement>,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val colorArgb: Int,
    val categoryName: String = "Important",
    val timestamp: Long = System.currentTimeMillis()
)
