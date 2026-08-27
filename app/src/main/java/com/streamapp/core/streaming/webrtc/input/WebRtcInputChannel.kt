package com.streamapp.core.streaming.webrtc.input

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.input.model.GamepadAxisType
import com.streamapp.core.input.model.GamepadButtonType
import com.streamapp.core.input.model.MouseButtonType
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.input.model.TouchAction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance, zero-allocation binary protocol serializer for WebRTC DataChannel input events.
 * Packet format:
 * [1 Byte EventType] [2 Bytes SequenceID] [Payload Bytes]
 */
@Singleton
class WebRtcInputChannel @Inject constructor() {

    private var sequenceId: Short = 0
    private val buffer = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN)

    companion object {
        const val TYPE_TOUCH: Byte = 0x01
        const val TYPE_MOUSE_MOVE: Byte = 0x02
        const val TYPE_MOUSE_BUTTON: Byte = 0x03
        const val TYPE_MOUSE_SCROLL: Byte = 0x04
        const val TYPE_GAMEPAD_BUTTON: Byte = 0x05
        const val TYPE_GAMEPAD_AXIS: Byte = 0x06
        const val TYPE_KEYBOARD_KEY: Byte = 0x07
        const val TYPE_TEXT_INPUT: Byte = 0x08
    }

    @Synchronized
    fun encodeEvent(event: NormalizedInputEvent): ByteBuffer {
        buffer.clear()
        sequenceId++

        when (event) {
            is NormalizedInputEvent.Touch -> {
                buffer.put(TYPE_TOUCH)
                buffer.putShort(sequenceId)
                buffer.put(event.action.ordinal.toByte())
                buffer.putInt(event.pointerId)
                buffer.putFloat(event.x)
                buffer.putFloat(event.y)
                buffer.putFloat(event.pressure)
            }
            is NormalizedInputEvent.MouseMove -> {
                buffer.put(TYPE_MOUSE_MOVE)
                buffer.putShort(sequenceId)
                buffer.putFloat(event.deltaX)
                buffer.putFloat(event.deltaY)
            }
            is NormalizedInputEvent.MouseButton -> {
                buffer.put(TYPE_MOUSE_BUTTON)
                buffer.putShort(sequenceId)
                buffer.put(event.button.ordinal.toByte())
                buffer.put(if (event.isDown) 1.toByte() else 0.toByte())
            }
            is NormalizedInputEvent.MouseScroll -> {
                buffer.put(TYPE_MOUSE_SCROLL)
                buffer.putShort(sequenceId)
                buffer.putFloat(event.scrollX)
                buffer.putFloat(event.scrollY)
            }
            is NormalizedInputEvent.GamepadButton -> {
                buffer.put(TYPE_GAMEPAD_BUTTON)
                buffer.putShort(sequenceId)
                buffer.put(event.button.ordinal.toByte())
                buffer.put(if (event.isPressed) 1.toByte() else 0.toByte())
                buffer.putFloat(event.pressure)
            }
            is NormalizedInputEvent.GamepadAxis -> {
                buffer.put(TYPE_GAMEPAD_AXIS)
                buffer.putShort(sequenceId)
                buffer.put(event.axis.ordinal.toByte())
                buffer.putFloat(event.value)
            }
            is NormalizedInputEvent.KeyboardKey -> {
                buffer.put(TYPE_KEYBOARD_KEY)
                buffer.putShort(sequenceId)
                buffer.putInt(event.keyCode)
                buffer.put(if (event.isDown) 1.toByte() else 0.toByte())
                buffer.putInt(event.modifiers)
            }
            is NormalizedInputEvent.TextInput -> {
                val bytes = event.text.toByteArray(Charsets.UTF_8)
                buffer.put(TYPE_TEXT_INPUT)
                buffer.putShort(sequenceId)
                buffer.putShort(bytes.size.toShort())
                buffer.put(bytes)
            }
        }

        buffer.flip()
        return buffer
    }
}
