package com.streamapp.core.input.physical

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.streamapp.core.input.model.GamepadAxisType
import com.streamapp.core.input.model.GamepadButtonType
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.input.normalizer.InputNormalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalGamepadHandler @Inject constructor(
    private val normalizer: InputNormalizer
) {

    fun handleKeyEvent(event: KeyEvent): NormalizedInputEvent.GamepadButton? {
        if ((event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
            (event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
        ) {
            return null
        }

        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButtonType.A
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButtonType.B
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButtonType.X
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButtonType.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButtonType.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButtonType.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButtonType.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButtonType.R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButtonType.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButtonType.R3
            KeyEvent.KEYCODE_DPAD_UP -> GamepadButtonType.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadButtonType.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadButtonType.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadButtonType.DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButtonType.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButtonType.SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> GamepadButtonType.HOME
            else -> return null
        }

        val isPressed = event.action == KeyEvent.ACTION_DOWN
        return NormalizedInputEvent.GamepadButton(button, isPressed)
    }

    fun handleMotionEvent(event: MotionEvent): List<NormalizedInputEvent> {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return emptyList()
        }

        val events = mutableListOf<NormalizedInputEvent>()

        // Left Thumbstick
        val rawLx = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLy = event.getAxisValue(MotionEvent.AXIS_Y)
        val (normLx, normLy) = normalizer.normalizeThumbstick(rawLx, rawLy)
        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.LEFT_STICK_X, normLx))
        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.LEFT_STICK_Y, normLy))

        // Right Thumbstick
        val rawRx = event.getAxisValue(MotionEvent.AXIS_Z)
        val rawRy = event.getAxisValue(MotionEvent.AXIS_RZ)
        val (normRx, normRy) = normalizer.normalizeThumbstick(rawRx, rawRy)
        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.RIGHT_STICK_X, normRx))
        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.RIGHT_STICK_Y, normRy))

        // Analog Triggers
        val rawL2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).takeIf { it != 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_BRAKE)
        val rawR2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).takeIf { it != 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_GAS)

        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.LEFT_TRIGGER, normalizer.normalizeTrigger(rawL2)))
        events.add(NormalizedInputEvent.GamepadAxis(GamepadAxisType.RIGHT_TRIGGER, normalizer.normalizeTrigger(rawR2)))

        return events
    }
}
