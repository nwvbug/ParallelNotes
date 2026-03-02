package com.nvemuri.parallelnotes.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    // We use UUIDs (strings) instead of auto-incrementing integers.
    // This is CRUCIAL for server syncing later so you don't get ID collisions!
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderId: String? = null, // Null means it's on the homepage, not in a folder
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)