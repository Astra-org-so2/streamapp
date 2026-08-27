package com.streamapp.core.input.normalizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InputNormalizerTest {

    private lateinit var normalizer: InputNormalizer

    @BeforeEach
    fun setUp() {
        normalizer = InputNormalizer()
    }

    @Test
    fun `when thumbstick is within deadzone, output must be zero`() {
        val (normX, normY) = normalizer.normalizeThumbstick(0.05f, 0.04f)
        assertEquals(0f, normX)
        assertEquals(0f, normY)
    }

    @Test
    fun `when thumbstick is pushed to max, output must be normalized to 1`() {
        val (normX, normY) = normalizer.normalizeThumbstick(1.0f, 0.0f)
        assertEquals(1.0f, normX, 0.001f)
        assertEquals(0.0f, normY, 0.001f)
    }

    @Test
    fun `when touch coordinates are processed, output must be normalized between 0 and 1`() {
        val (normX, normY) = normalizer.normalizeTouch(
            touchX = 960f,
            touchY = 540f,
            containerWidth = 1920f,
            containerHeight = 1080f
        )
        assertEquals(0.5f, normX, 0.001f)
        assertEquals(0.5f, normY, 0.001f)
    }

    @Test
    fun `when touch is out of bounds, output is clamped to valid range`() {
        val (normX, normY) = normalizer.normalizeTouch(
            touchX = -50f,
            touchY = 2000f,
            containerWidth = 1920f,
            containerHeight = 1080f
        )
        assertEquals(0.0f, normX)
        assertEquals(1.0f, normY)
    }
}
