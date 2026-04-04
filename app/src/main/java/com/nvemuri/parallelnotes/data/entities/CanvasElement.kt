package com.nvemuri.parallelnotes.data.entities

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import android.graphics.Picture
import androidx.compose.ui.graphics.toArgb
import java.util.UUID
import kotlinx.serialization.Serializable

sealed interface CanvasElement {
    val id: String
    val zIndex: Float

    // Bounding coordinates
    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float

    // A convenience property that will make your Lasso and Tiling math much easier later
    val boundingBox: Rect
        get() = Rect(minX, minY, maxX, maxY)

    // A standard contract ensuring every element knows how to move itself
    fun translate(dx: Float, dy: Float): CanvasElement
}

data class Point(val offset: Offset, val pressure: Float)



data class PenStroke(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Float = 0f, // Ink defaults to the bottom layer
    val rawPoints: List<Point>, // Original hardware points for saving
    val points: List<Point>,    // Smoothed points for runtime rendering/hit detection
    val arcSmoothing: Boolean,  // Whether this stroke was smoothed
    val thickness: Float,
    val color: Color,
    val picture: Picture,
    override val minX: Float,
    override val maxX: Float,
    override val minY: Float,
    override val maxY: Float
) : CanvasElement {

    override fun translate(dx: Float, dy: Float): PenStroke {
        val translatePoint = { p: Point ->
            Point(Offset(p.offset.x + dx, p.offset.y + dy), p.pressure)
        }
        return copy(
            rawPoints = rawPoints.map(translatePoint),
            points = points.map(translatePoint),
            minX = minX + dx,
            maxX = maxX + dx,
            minY = minY + dy,
            maxY = maxY + dy
        )
    }
}


@Serializable
data class SerializablePoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

@Serializable
data class SerializableElement(
    val id: String,
    val zIndex: Float,
    val type: String, // "PEN", "IMAGE", etc.
    val version: Int = 0, // 0 = Old (already smoothed), 1 = New (raw points)
    // Stroke specific data
    val points: List<SerializablePoint> = emptyList(), // We'll store rawPoints here now
    val arcSmoothing: Boolean = false,
    val thickness: Float = 0f,
    val colorArgb: Int = 0,
    // Bounding box
    val minX: Float, val maxX: Float, val minY: Float, val maxY: Float
)

fun CanvasElement.toSerializable(): SerializableElement {
    return when (this) {
        is PenStroke -> SerializableElement(
            id = this.id,
            zIndex = this.zIndex,
            type = "PEN",
            version = 1, // Mark as Version 1 so we know to smooth on load next time
            // WE ONLY SAVE THE RAW POINTS NOW
            points = this.rawPoints.map { SerializablePoint(it.offset.x, it.offset.y, it.pressure) },
            arcSmoothing = this.arcSmoothing,
            thickness = this.thickness,
            colorArgb = this.color.toArgb(),
            minX = this.minX, maxX = this.maxX, minY = this.minY, maxY = this.maxY
        )
        else -> throw IllegalArgumentException("Unknown element type")
    }
}
