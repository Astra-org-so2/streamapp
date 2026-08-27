package com.streamapp.core.input.physical

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.streamapp.core.input.model.GamepadAxisType
import com.streamapp.core.input.model.GamepadButtonType
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.input.normalizer.InputNormalizer
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PhysicalGamepadHandler @Inject constructor(
    private val normalizer: InputNormalizer
) {
    private val previousAxisValues = EnumMap<GamepadAxisType, Float>(GamepadAxisType::class.java)
    private val axisDeltaThreshold = 0.008f

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
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return emptyList()
        }

        val device = event.device
        val events = mutableListOf<NormalizedInputEvent>()

        // 1. Left Thumbstick (AXIS_X, AXIS_Y)
        val rawLx = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLy = event.getAxisValue(MotionEvent.AXIS_Y)
        val (normLx, normLy) = normalizer.normalizeThumbstick(rawLx, rawLy)

        checkAndAddAxis(events, GamepadAxisType.LEFT_STICK_X, normLx)
        checkAndAddAxis(events, GamepadAxisType.LEFT_STICK_Y, normLy)

        // 2. Right Thumbstick (AXIS_Z/AXIS_RZ or fallback AXIS_RX/AXIS_RY)
        val hasZ = device?.getMotionRange(MotionEvent.AXIS_Z) != null
        val rawRx = if (hasZ) event.getAxisValue(MotionEvent.AXIS_Z) else event.getAxisValue(MotionEvent.AXIS_RX)
        val rawRy = if (hasZ) event.getAxisValue(MotionEvent.AXIS_RZ) else event.getAxisValue(MotionEvent.AXIS_RY)
        val (normRx, normRy) = normalizer.normalizeThumbstick(rawRx, rawRy)

        checkAndAddAxis(events, GamepadAxisType.RIGHT_STICK_X, normRx)
        checkAndAddAxis(events, GamepadAxisType.RIGHT_STICK_Y, normRy)

        // 3. Analog Triggers (AXIS_LTRIGGER/AXIS_BRAKE, AXIS_RTRIGGER/AXIS_GAS)
        val hasLTrigger = device?.getMotionRange(MotionEvent.AXIS_LTRIGGER) != null
        val rawL2 = if (hasLTrigger) event.getAxisValue(MotionEvent.AXIS_LTRIGGER) else event.getAxisValue(MotionEvent.AXIS_BRAKE)

        val hasRTrigger = device?.getMotionRange(MotionEvent.AXIS_RTRIGGER) != null
        val rawR2 = if (hasRTrigger) event.getAxisValue(MotionEvent.AXIS_RTRIGGER) else event.getAxisValue(MotionEvent.AXIS_GAS)

        checkAndAddAxis(events, GamepadAxisType.LEFT_TRIGGER, normalizer.normalizeTrigger(rawL2))
        checkAndAddAxis(events, GamepadAxisType.RIGHT_TRIGGER, normalizer.normalizeTrigger(rawR2))

        return events
    }

    private fun checkAndAddAxis(
        outList: MutableList<NormalizedInputEvent>,
        axisType: GamepadAxisType,
        newValue: Float
    ) {
        val prev = previousAxisValues[axisType] ?: 0f
        if (abs(newValue - prev) >= axisDeltaThreshold || (newValue == 0f && prev != 0f)) {
            previousAxisValues[axisType] = newValue
            outList.add(NormalizedInputEvent.GamepadAxis(axisType, newValue))
        }
    }
}
