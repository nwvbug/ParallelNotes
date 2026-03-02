package com.nvemuri.parallelnotes.data.entities

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import android.graphics.Picture
import java.util.UUID

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
    val points: List<Point>,
    val thickness: Float,
    val color: Color,
    val picture: Picture,
    override val minX: Float,
    override val maxX: Float,
    override val minY: Float,
    override val maxY: Float
) : CanvasElement {

    override fun translate(dx: Float, dy: Float): PenStroke {
        return copy(
            // Shift all point coordinates
            points = points.map {
                Point(Offset(it.offset.x + dx, it.offset.y + dy), it.pressure)
            },
            // Shift the bounding box
            minX = minX + dx,
            maxX = maxX + dx,
            minY = minY + dy,
            maxY = maxY + dy
            // Notice we don't change the Picture. Your draw logic already handles
            // translating the canvas before drawing the picture, which is highly efficient!
        )
    }
}