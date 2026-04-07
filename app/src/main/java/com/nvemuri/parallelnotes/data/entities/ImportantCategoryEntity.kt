package com.nvemuri.parallelnotes.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "important_categories")
data class ImportantCategoryEntity(
    @PrimaryKey val name: String,
    val colorArgb: Int
)
