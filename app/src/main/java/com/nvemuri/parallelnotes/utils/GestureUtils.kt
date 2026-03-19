package com.nvemuri.parallelnotes.utils
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass

suspend fun PointerInputScope.detectMultiFingerTap(
    onTwoFingerTap: () -> Unit,
    onThreeFingerTap: () -> Unit
){
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val startTime = System.currentTimeMillis()
        var pointerCount = 1
        var isDrag = false

        do {
            val event = awaitPointerEvent(PointerEventPass.Main)

            if (event.changes.size > pointerCount) {
                pointerCount = event.changes.size
            }

            event.changes.forEach { change ->
                val movement = change.position - change.previousPosition
                val distance = movement.getDistance()
                if (distance > 15f){
                    isDrag = true
                }
            }

        } while (event.changes.any { it.pressed })
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (!isDrag && duration < 400){
            if (pointerCount == 2){
                onTwoFingerTap()
            } else if (pointerCount == 3){
                onThreeFingerTap()
            }
        }
    }
}