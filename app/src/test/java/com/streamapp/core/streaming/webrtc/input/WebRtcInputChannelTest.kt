package com.streamapp.core.streaming.webrtc.input

import com.streamapp.core.input.model.GamepadButtonType
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.input.model.TouchAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebRtcInputChannelTest {

    private lateinit var channel: WebRtcInputChannel

    @BeforeEach
    fun setUp() {
        channel = WebRtcInputChannel()
    }

    @Test
    fun `when encoding touch event, header and payload match binary spec`() {
        val touch = NormalizedInputEvent.Touch(
            action = TouchAction.DOWN,
            pointerId = 1,
            x = 0.5f,
            y = 0.75f,
            pressure = 1.0f
        )

        val buffer = channel.encodeEvent(touch)
        assertTrue(buffer.remaining() > 0)

        val type = buffer.get()
        val seq = buffer.getShort()
        val action = buffer.get()
        val pointerId = buffer.getInt()
        val x = buffer.getFloat()
        val y = buffer.getFloat()
        val pressure = buffer.getFloat()

        assertEquals(WebRtcInputChannel.TYPE_TOUCH, type)
        assertEquals(TouchAction.DOWN.ordinal.toByte(), action)
        assertEquals(1, pointerId)
        assertEquals(0.5f, x, 0.001f)
        assertEquals(0.75f, y, 0.001f)
        assertEquals(1.0f, pressure, 0.001f)
    }

    @Test
    fun `when encoding gamepad button event, serialized byte buffer is correct`() {
        val buttonEvent = NormalizedInputEvent.GamepadButton(
            button = GamepadButtonType.A,
            isPressed = true,
            pressure = 1.0f
        )

        val buffer = channel.encodeEvent(buttonEvent)
        val type = buffer.get()
        val seq = buffer.getShort()
        val buttonOrdinal = buffer.get()
        val isPressedByte = buffer.get()

        assertEquals(WebRtcInputChannel.TYPE_GAMEPAD_BUTTON, type)
        assertEquals(GamepadButtonType.A.ordinal.toByte(), buttonOrdinal)
        assertEquals(1.toByte(), isPressedByte)
    }
}
