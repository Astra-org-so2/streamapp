package com.streamapp.core.input.model

import kotlinx.serialization.Serializable

/**
 * Platform-agnostic normalized input event model.
 * All touch, physical gamepad, mouse, keyboard, and virtual controller events
 * are converted into this model before transmission to the streaming host.
 */
@Serializable
sealed interface NormalizedInputEvent {

    @Serializable
    data class Touch(
        val action: TouchAction,
        val pointerId: Int,
        val x: Float, // Normalized 0.0f .. 1.0f
        val y: Float, // Normalized 0.0f .. 1.0f
        val pressure: Float = 1.0f
    ) : NormalizedInputEvent

    @Serializable
    data class MouseMove(
        val deltaX: Float,
        val deltaY: Float
    ) : NormalizedInputEvent

    @Serializable
    data class MouseButton(
        val button: MouseButtonType,
        val isDown: Boolean
    ) : NormalizedInputEvent

    @Serializable
    data class MouseScroll(
        val scrollX: Float,
        val scrollY: Float
    ) : NormalizedInputEvent

    @Serializable
    data class GamepadButton(
        val button: GamepadButtonType,
        val isPressed: Boolean,
        val pressure: Float = 1.0f
    ) : NormalizedInputEvent

    @Serializable
    data class GamepadAxis(
        val axis: GamepadAxisType,
        val value: Float // Clamped -1.0f .. 1.0f
    ) : NormalizedInputEvent

    @Serializable
    data class KeyboardKey(
        val keyCode: Int,
        val isDown: Boolean,
        val modifiers: Int = 0
    ) : NormalizedInputEvent

    @Serializable
    data class TextInput(
        val text: String
    ) : NormalizedInputEvent
}

@Serializable
enum class TouchAction {
    DOWN,
    MOVE,
    UP,
    CANCEL
}

@Serializable
enum class MouseButtonType {
    LEFT,
    RIGHT,
    MIDDLE,
    BACK,
    FORWARD
}

@Serializable
enum class GamepadButtonType {
    A, B, X, Y,
    L1, R1, L2, R2,
    L3, R3,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    START, SELECT, HOME
}

@Serializable
enum class GamepadAxisType {
    LEFT_STICK_X,
    LEFT_STICK_Y,
    RIGHT_STICK_X,
    RIGHT_STICK_Y,
    LEFT_TRIGGER,
    RIGHT_TRIGGER
}
