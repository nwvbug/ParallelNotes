package com.nvemuri.parallelnotes.utils
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType

suspend fun PointerInputScope.detectMultiFingerTap(
    onTwoFingerTap: () -> Unit,
    onThreeFingerTap: () -> Unit
){
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        
        // Ignore if the initial touch is a stylus (likely drawing, not tapping for undo/redo)
        if (down.type == PointerType.Stylus) return@awaitEachGesture
        
        var pointerCount = 1
        var isDrag = false
        var stylusDetected = false
        var multiFingerStartTime = 0L
 
        do {
            val event = awaitPointerEvent(PointerEventPass.Main)

            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount > pointerCount) {
                pointerCount = pressedCount
                if (pressedCount >= 2 && multiFingerStartTime == 0L) {
                    multiFingerStartTime = System.currentTimeMillis()
                }
            }

            if (event.changes.any { it.type == PointerType.Stylus }) {
                stylusDetected = true
            }

            event.changes.forEach { change ->
                val movement = change.position - change.previousPosition
                val distance = movement.getDistance()
                if (distance > 20f){
                    isDrag = true
                }
            }

        } while (event.changes.any { it.pressed })

        val multiFingerDuration = if (multiFingerStartTime > 0L) System.currentTimeMillis() - multiFingerStartTime else 0L

        // Require both fingers were held simultaneously for 40–200ms to avoid accidental hand rests
        if (!isDrag && !stylusDetected && multiFingerDuration in 40L..200L){
            if (pointerCount == 2){
                onTwoFingerTap()
            } else if (pointerCount == 3){
                onThreeFingerTap()
            }
        }
    }
}