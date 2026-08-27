package com.streamapp.core.input.keyboard

import android.view.KeyEvent
import com.streamapp.core.input.model.NormalizedInputEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualKeyboardBridge @Inject constructor() {

    fun mapKeyEvent(event: KeyEvent): NormalizedInputEvent.KeyboardKey {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        return NormalizedInputEvent.KeyboardKey(
            keyCode = event.keyCode,
            isDown = isDown,
            modifiers = event.modifiers
        )
    }

    fun mapTextInput(text: String): NormalizedInputEvent.TextInput {
        return NormalizedInputEvent.TextInput(text)
    }
}
