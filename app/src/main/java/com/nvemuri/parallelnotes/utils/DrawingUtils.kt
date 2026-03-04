package com.nvemuri.parallelnotes.utils

import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.data.entities.PenStroke

// Drawing Functions
fun DrawScope.drawStroke( //should ideally replace this entirely with drawpath eventually
    stroke: PenStroke,
    width: Float,
    selected: Boolean = false
){
    if (stroke.points.size < 2) return
    for (i in 1 until stroke.points.size){
        val p1 = stroke.points[i - 1]
        val p2 = stroke.points[i]
        val mappedPressure = 0.2f + (p2.pressure * 0.8f)
        val strokeThickness = width * mappedPressure

        drawLine(
            color = stroke.color,
            start = p1.offset,
            end = p2.offset,
            strokeWidth = strokeThickness,
            cap = StrokeCap.Round
        )

        //shadow for selected (not used anymore)
        if (selected){
            val shadowPaint = Paint().apply {
                color = stroke.color
                strokeWidth = strokeThickness
                strokeCap = StrokeCap.Round

                // 2. Access the native Android paint to add the shadow layer
                asFrameworkPaint().apply {
                    setShadowLayer(
                        15f, // Blur radius (how soft the shadow is)
                        3f,  // X offset (dx)
                        5f, // Y offset (dy)
                        android.graphics.Color.argb(50, 0, 0, 0) // Shadow color & alpha
                    )
                }
            }

            drawIntoCanvas { canvas ->
                canvas.drawLine(
                    p1 = p1.offset,
                    p2 = p2.offset,
                    paint = shadowPaint
                )
            }
        }
    }
}

// Smoothing
//fun smoothStroke(stroke: PenStroke): PenStroke{ //Basic average smoothing
//    val rawPoints = stroke.points
//    if (rawPoints.size < 3) return stroke
//    val smoothedList = mutableListOf<Point>()
//    smoothedList.add(rawPoints.first())
//    for (i in 1 until rawPoints.size - 1) {
//        val prev = rawPoints[i - 1]
//        val current = rawPoints[i]
//        val next = rawPoints[i + 1]
//
//        val smoothedX = (prev.offset.x + current.offset.x + next.offset.x) / 3f
//        val smoothedY = (prev.offset.y + current.offset.y + next.offset.y) / 3f
//
//        val smoothedPressure = (prev.pressure + current.pressure + next.pressure) / 3f
//
//        smoothedList.add(Point(Offset(smoothedX, smoothedY), smoothedPressure))
//    }
//
//    // Keep the very last point exactly where it is
//    smoothedList.add(rawPoints.last())
//
//    return PenStroke(smoothedList, stroke.thickness, stroke.color)
//}

fun bezierSmoothStroke(rawPoints: List<Point>, steps: Int = 10): List<Point>{
    if (rawPoints.size < 3) return rawPoints

    val smoothPoints = mutableListOf<Point>()
    smoothPoints.add(rawPoints.first())

    for (i in 1 until rawPoints.size - 1) {
        val p0 = rawPoints[i - 1]
        val p1 = rawPoints[i]
        val p2 = rawPoints[i + 1]

        val startX = (p0.offset.x + p1.offset.x) / 2f
        val startY = (p0.offset.y + p1.offset.y) / 2f
        val startPressure = (p0.pressure + p1.pressure) / 2f

        val endX = (p1.offset.x + p2.offset.x) / 2f
        val endY = (p1.offset.y + p2.offset.y) / 2f
        val endPressure = (p1.pressure + p2.pressure) / 2f

        for (step in 0..steps){
            val t = step.toFloat() / steps
            val inverseT = 1.0f - t
            val x = (inverseT * inverseT * startX) +
                    (2 * inverseT * t * p1.offset.x) +
                    (t * t * endX)

            val y = (inverseT * inverseT * startY) +
                    (2 * inverseT * t * p1.offset.y) +
                    (t * t * endY)

            val pressure = startPressure + (endPressure - startPressure) * t

            smoothPoints.add(Point(Offset(x, y), pressure))
        }
    }
    val lastPressure = (rawPoints[rawPoints.size-2].pressure + rawPoints[rawPoints.size-1].pressure)/2
    val lastPoint = Point(rawPoints.last().offset, lastPressure)
    smoothPoints.add(lastPoint)
    return smoothPoints
}

// Lasso math, takes a point on a stroke and determines if it is inside the drawn lasso loop
fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    var isInside = false
    var j = polygon.size - 1 // Start with the last vertex to close the loop

    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]

        // Does the horizontal ray cross the Y-bounds of the edge?
        val intersectsY = (pi.y > point.y) != (pj.y > point.y)

        if (intersectsY) {
            // Calculate the exact X coordinate where the ray hits the edge
            val intersectX = pi.x + (point.y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)

            // If the point is to the left of the intersection, toggle the boolean
            if (point.x < intersectX) {
                isInside = !isInside
            }
        }
        j = i
    }
    return isInside
}

fun generatePathFromPoints(points: List<Point>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    path.moveTo(points.first().offset.x, points.first().offset.y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].offset.x, points[i].offset.y)
    }
    return path
}


// Chunking Functions
// get which chunks a given canvaselem touches
fun getOverlappingChunkKeys(bounds: Rect, CHUNK_SIZE: Int): List<String> {
    val keys = mutableListOf<String>()

    // Find the min and max chunk coordinates
    val minGridX = (bounds.left / CHUNK_SIZE).toInt()
    val minGridY = (bounds.top / CHUNK_SIZE).toInt()
    val maxGridX = (bounds.right / CHUNK_SIZE).toInt()
    val maxGridY = (bounds.bottom / CHUNK_SIZE).toInt()

    for (x in minGridX..maxGridX) {
        for (y in minGridY..maxGridY) {
            keys.add("$x,$y")
        }
    }
    return keys
}