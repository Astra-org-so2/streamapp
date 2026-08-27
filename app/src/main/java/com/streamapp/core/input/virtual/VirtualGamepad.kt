package com.streamapp.core.input.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamapp.core.designsystem.theme.AccentCyan
import com.streamapp.core.designsystem.theme.PillShape
import com.streamapp.core.designsystem.theme.SurfaceDark
import com.streamapp.core.input.model.GamepadAxisType
import com.streamapp.core.input.model.GamepadButtonType
import com.streamapp.core.input.model.NormalizedInputEvent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualGamepadOverlay(
    onInputEvent: (NormalizedInputEvent) -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.65f,
    scale: Float = 1.0f
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // Left Analog Stick (Bottom-Left)
        VirtualThumbstick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 32.dp * scale, y = (-32).dp * scale),
            size = 140.dp * scale,
            opacity = opacity,
            onAxisChanged = { x, y ->
                onInputEvent(NormalizedInputEvent.GamepadAxis(GamepadAxisType.LEFT_STICK_X, x))
                onInputEvent(NormalizedInputEvent.GamepadAxis(GamepadAxisType.LEFT_STICK_Y, y))
            }
        )

        // Right Analog Stick (Bottom-Right)
        VirtualThumbstick(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-32).dp * scale, y = (-32).dp * scale),
            size = 140.dp * scale,
            opacity = opacity,
            onAxisChanged = { x, y ->
                onInputEvent(NormalizedInputEvent.GamepadAxis(GamepadAxisType.RIGHT_STICK_X, x))
                onInputEvent(NormalizedInputEvent.GamepadAxis(GamepadAxisType.RIGHT_STICK_Y, y))
            }
        )

        // ABXY Diamond (Above Right Stick)
        AbxyCluster(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-36).dp * scale, y = (-180).dp * scale),
            opacity = opacity,
            scale = scale,
            onButtonPress = { button, isPressed ->
                onInputEvent(NormalizedInputEvent.GamepadButton(button, isPressed))
            }
        )

        // D-Pad (Above Left Stick)
        DPadCluster(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 36.dp * scale, y = (-180).dp * scale),
            opacity = opacity,
            scale = scale,
            onButtonPress = { button, isPressed ->
                onInputEvent(NormalizedInputEvent.GamepadButton(button, isPressed))
            }
        )

        // Shoulder & Triggers (Top corners)
        ShoulderButton(
            label = "L1",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 32.dp * scale, y = 32.dp * scale),
            opacity = opacity,
            onPress = { isPressed ->
                onInputEvent(NormalizedInputEvent.GamepadButton(GamepadButtonType.L1, isPressed))
            }
        )

        ShoulderButton(
            label = "R1",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32).dp * scale, y = 32.dp * scale),
            opacity = opacity,
            onPress = { isPressed ->
                onInputEvent(NormalizedInputEvent.GamepadButton(GamepadButtonType.R1, isPressed))
            }
        )
    }
}

@Composable
fun VirtualThumbstick(
    onAxisChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    opacity: Float = 0.65f
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val outerRadiusPx = with(density) { (size / 2f).toPx() }
    val thumbRadiusPx = with(density) { (size * 0.45f / 2f).toPx() }
    val maxRadiusPx = (outerRadiusPx - thumbRadiusPx).coerceAtLeast(1f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = opacity * 0.7f))
            .border(2.dp, AccentCyan.copy(alpha = opacity * 0.8f), CircleShape)
            .pointerInput(maxRadiusPx) {
                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onAxisChanged(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onAxisChanged(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        val clampedDistance = min(distance, maxRadiusPx)
                        val angle = atan2(newOffset.y, newOffset.x)

                        thumbOffset = Offset(
                            clampedDistance * cos(angle),
                            clampedDistance * sin(angle)
                        )

                        val normalizedX = (thumbOffset.x / maxRadiusPx).coerceIn(-1f, 1f)
                        val normalizedY = (thumbOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
                        onAxisChanged(normalizedX, normalizedY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(size * 0.45f)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = opacity * 0.9f))
                .border(2.dp, Color.White.copy(alpha = opacity), CircleShape)
        )
    }
}

@Composable
fun AbxyCluster(
    onButtonPress: (GamepadButtonType, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.65f,
    scale: Float = 1.0f
) {
    val buttonSize = 44.dp * scale
    val spacing = 48.dp * scale

    Box(modifier = modifier.size(spacing * 2.8f), contentAlignment = Alignment.Center) {
        // Y (Top)
        GamepadRoundButton(
            label = "Y",
            modifier = Modifier.offset(y = -spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.Y, isPressed) }
        )
        // A (Bottom)
        GamepadRoundButton(
            label = "A",
            modifier = Modifier.offset(y = spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.A, isPressed) }
        )
        // X (Left)
        GamepadRoundButton(
            label = "X",
            modifier = Modifier.offset(x = -spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.X, isPressed) }
        )
        // B (Right)
        GamepadRoundButton(
            label = "B",
            modifier = Modifier.offset(x = spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.B, isPressed) }
        )
    }
}

@Composable
fun DPadCluster(
    onButtonPress: (GamepadButtonType, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.65f,
    scale: Float = 1.0f
) {
    val buttonSize = 42.dp * scale
    val spacing = 46.dp * scale

    Box(modifier = modifier.size(spacing * 2.8f), contentAlignment = Alignment.Center) {
        // UP
        GamepadRoundButton(
            label = "▲",
            modifier = Modifier.offset(y = -spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.DPAD_UP, isPressed) }
        )
        // DOWN
        GamepadRoundButton(
            label = "▼",
            modifier = Modifier.offset(y = spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.DPAD_DOWN, isPressed) }
        )
        // LEFT
        GamepadRoundButton(
            label = "◀",
            modifier = Modifier.offset(x = -spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.DPAD_LEFT, isPressed) }
        )
        // RIGHT
        GamepadRoundButton(
            label = "▶",
            modifier = Modifier.offset(x = spacing),
            size = buttonSize,
            opacity = opacity,
            onPress = { isPressed -> onButtonPress(GamepadButtonType.DPAD_RIGHT, isPressed) }
        )
    }
}

@Composable
fun GamepadRoundButton(
    label: String,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    opacity: Float = 0.65f
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isPressed) AccentCyan.copy(alpha = opacity)
                else SurfaceDark.copy(alpha = opacity * 0.8f)
            )
            .border(
                1.5.dp,
                if (isPressed) Color.White.copy(alpha = opacity)
                else AccentCyan.copy(alpha = opacity * 0.7f),
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPress(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) SurfaceDark else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ShoulderButton(
    label: String,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.65f
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(width = 72.dp, height = 36.dp)
            .clip(PillShape)
            .background(
                if (isPressed) AccentCyan.copy(alpha = opacity)
                else SurfaceDark.copy(alpha = opacity * 0.8f)
            )
            .border(1.5.dp, AccentCyan.copy(alpha = opacity * 0.7f), PillShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPress(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) SurfaceDark else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
