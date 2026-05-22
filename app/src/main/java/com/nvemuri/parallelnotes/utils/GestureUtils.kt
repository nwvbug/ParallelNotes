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
        
        val uniquePointerIds = mutableSetOf(down.id)
        var isDrag = false
        var stylusDetected = false
        var twoFingerStartTime = 0L

        do {
            val event = awaitPointerEvent(PointerEventPass.Main)

            event.changes.forEach { change ->
                if (change.pressed) {
                    uniquePointerIds.add(change.id)
                }
            }

            if (uniquePointerIds.size >= 2 && twoFingerStartTime == 0L) {
                twoFingerStartTime = System.currentTimeMillis()
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

        val twoFingerDuration = if (twoFingerStartTime > 0L) System.currentTimeMillis() - twoFingerStartTime else 0L

        // Require fingers held simultaneously for 40–400ms to avoid accidental hand rests.
        // Both checks use twoFingerStartTime because that's when the multi-finger gesture begins —
        // by the time the 3rd finger lands the user is already lifting off, so threeFingerDuration
        // is almost always < 40ms and would never pass a lower-bound check.
        val fingerCount = uniquePointerIds.size
        if (!isDrag && !stylusDetected) {
            if (fingerCount == 2 && twoFingerDuration in 40L..400L){
                onTwoFingerTap()
            } else if (fingerCount == 3 && twoFingerDuration in 40L..400L){
                onThreeFingerTap()
            }
        }
    }
}