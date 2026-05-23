package com.nvemuri.parallelnotes.data.entities

sealed class CanvasAction {
    data class AddElements(val elements: List<CanvasElement>) : CanvasAction()
    data class RemoveElements(val elements: List<CanvasElement>) : CanvasAction()
    data class MoveElements(val elements: List<CanvasElement>, val dx: Float, val dy: Float) : CanvasAction()
    data class ResizeElements(
        val originalElements: List<CanvasElement>,  // state before resize
        val resizedElements: List<CanvasElement>    // state after resize
    ) : CanvasAction()
}
