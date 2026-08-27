package com.streamapp.core.features.heatmap

import android.view.MotionEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TouchPoint(val x: Float, val y: Float, val timestamp: Long)

@Singleton
class TouchHeatmapTracker @Inject constructor() {

    private val _touchPoints = MutableStateFlow<List<TouchPoint>>(emptyList())
    val touchPoints: StateFlow<List<TouchPoint>> = _touchPoints.asStateFlow()

    fun recordTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val point = TouchPoint(
                x = event.x,
                y = event.y,
                timestamp = System.currentTimeMillis()
            )
            _touchPoints.update { current ->
                val updated = current.toMutableList()
                updated.add(point)
                if (updated.size > 1000) {
                    updated.removeAt(0)
                }
                updated
            }
        }
    }

    fun clearHeatmap() {
        _touchPoints.value = emptyList()
    }
}
