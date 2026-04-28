package com.nvemuri.parallelnotes.utils

import android.graphics.Bitmap
import android.graphics.Picture
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.nvemuri.parallelnotes.data.entities.PenStyle
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.data.entities.PenStroke
import kotlin.math.floor

// Drawing Functions
fun DrawScope.drawStroke(
    stroke: PenStroke,
    width: Float,
    selected: Boolean = false
){
    if (stroke.points.size < 2) return

    when (stroke.penStyle) {
        PenStyle.SOLID -> {
            for (i in 1 until stroke.points.size) {
                val p1 = stroke.points[i - 1]
                val p2 = stroke.points[i]
                val strokeThickness = width * (0.2f + (p2.pressure * 0.8f))
                drawLine(color = stroke.color, start = p1.offset, end = p2.offset, strokeWidth = strokeThickness, cap = StrokeCap.Round)
            }
        }
        PenStyle.DASHED -> {
            val path = Path()
            path.moveTo(stroke.points.first().offset.x, stroke.points.first().offset.y)
            stroke.points.drop(1).forEach { p -> path.lineTo(p.offset.x, p.offset.y) }
            val avgPressure = stroke.points.map { 0.2f + it.pressure * 0.8f }.average().toFloat()
            drawPath(
                path = path,
                color = stroke.color,
                style = Stroke(
                    width = width * avgPressure,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 1.5f, width * 1f), 0f)
                )
            )
        }
        PenStyle.HIGHLIGHTER -> {
            val path = Path()
            path.moveTo(stroke.points.first().offset.x, stroke.points.first().offset.y)
            stroke.points.drop(1).forEach { p -> path.lineTo(p.offset.x, p.offset.y) }
            drawPath(
                path = path,
                color = stroke.color.copy(alpha = 0.4f),
                style = Stroke(width = width * 1.5f, cap = StrokeCap.Square)
            )
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

    // must use floor(), otherwise -0.5 truncates to 0, breaking negative chunks
    val minGridX = floor(bounds.left / CHUNK_SIZE).toInt()
    val minGridY = floor(bounds.top / CHUNK_SIZE).toInt()
    val maxGridX = floor(bounds.right / CHUNK_SIZE).toInt()
    val maxGridY = floor(bounds.bottom / CHUNK_SIZE).toInt()

    for (x in minGridX..maxGridX) {
        for (y in minGridY..maxGridY) {
            keys.add("$x,$y")
        }
    }
    return keys
}

// Image Picture Utility
fun createImagePicture(bitmap: Bitmap, width: Float, height: Float): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), null)
    picture.endRecording()
    return picture
}