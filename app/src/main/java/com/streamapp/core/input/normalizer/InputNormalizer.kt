package com.streamapp.core.input.normalizer

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class InputNormalizer @Inject constructor() {

    private val deadzoneThreshold = 0.10f

    /**
     * Applies radial deadzone filtering and remapping to thumbstick axes with input clamping.
     */
    fun normalizeThumbstick(rawX: Float, rawY: Float): Pair<Float, Float> {
        val clampedX = rawX.coerceIn(-1.0f, 1.0f)
        val clampedY = rawY.coerceIn(-1.0f, 1.0f)

        val magnitude = sqrt(clampedX * clampedX + clampedY * clampedY)
        if (magnitude < deadzoneThreshold) {
            return 0f to 0f
        }

        // Remap magnitude linearly from [deadzoneThreshold .. 1.0] to [0.0 .. 1.0]
        val normalizedMagnitude = (magnitude - deadzoneThreshold) / (1.0f - deadzoneThreshold)
        val clampedMagnitude = normalizedMagnitude.coerceIn(0f, 1f)

        val normalizedX = (clampedX / magnitude) * clampedMagnitude
        val normalizedY = (clampedY / magnitude) * clampedMagnitude

        return normalizedX.coerceIn(-1f, 1f) to normalizedY.coerceIn(-1f, 1f)
    }

    /**
     * Normalizes analog trigger input [0.0 .. 1.0] with deadzone filtering and pre-clamping.
     */
    fun normalizeTrigger(rawValue: Float): Float {
        val clamped = rawValue.coerceIn(0f, 1f)
        if (clamped < 0.05f) return 0f
        return ((clamped - 0.05f) / 0.95f).coerceIn(0f, 1f)
    }

    /**
     * Normalizes screen touch coordinates to [0.0f .. 1.0f] relative to video container width/height.
     */
    fun normalizeTouch(
        touchX: Float,
        touchY: Float,
        containerWidth: Float,
        containerHeight: Float
    ): Pair<Float, Float> {
        val normX = (touchX / containerWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val normY = (touchY / containerHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return normX to normY
    }
}
