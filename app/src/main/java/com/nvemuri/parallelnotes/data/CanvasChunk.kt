package com.nvemuri.parallelnotes.data
import androidx.compose.ui.geometry.Rect

class CanvasChunk (val gridX: Int, val gridY: Int, val chunkSize: Int) {
    val bitmap = android.graphics.Bitmap.createBitmap(
        chunkSize, chunkSize, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)

    // coordinates of this chunk
    val bounds = Rect(
        left = (gridX * chunkSize).toFloat(),
        top = (gridY * chunkSize).toFloat(),
        right = ((gridX + 1) * chunkSize).toFloat(),
        bottom = ((gridY + 1) * chunkSize).toFloat()
    )

    fun clear() {
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
    }
}