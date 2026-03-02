package com.nvemuri.parallelnotes.data

import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nvemuri.parallelnotes.utils.Point
import com.nvemuri.parallelnotes.utils.PenStroke

// safe, purely primitive data class for the hard drive
data class StoragePoint(val x: Float, val y: Float, val pressure: Float)

object StrokeConverter {
    private val gson = Gson()

    // UI -> Database
    fun pointsToJson(points: List<Point>): String {
        val storagePoints = points.map {
            StoragePoint(x = it.offset.x, y = it.offset.y, pressure = it.pressure)
        }
        return gson.toJson(storagePoints)
    }

    // Database -> UI
    fun jsonToPoints(json: String): List<Point> {
        val type = object : TypeToken<List<StoragePoint>>() {}.type
        val storagePoints: List<StoragePoint> = gson.fromJson(json, type)

        return storagePoints.map {
            Point(offset = Offset(it.x, it.y), pressure = it.pressure)
        }
    }
}