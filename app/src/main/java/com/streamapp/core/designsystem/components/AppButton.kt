package com.streamapp.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.streamapp.core.designsystem.theme.AccentCyan
import com.streamapp.core.designsystem.theme.BackgroundDark
import com.streamapp.core.designsystem.theme.PillShape
import com.streamapp.core.designsystem.theme.SurfaceBorderDark
import com.streamapp.core.designsystem.theme.SurfaceVariantDark
import com.streamapp.core.designsystem.theme.TextPrimary

enum class AppButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    DANGER
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    shape: Shape = PillShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    when (variant) {
        AppButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = BackgroundDark,
                    disabledContainerColor = SurfaceVariantDark,
                    disabledContentColor = Color.Gray
                ),
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                content = content
            )
        }
        AppButtonVariant.SECONDARY -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceVariantDark,
                    contentColor = TextPrimary
                ),
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                content = content
            )
        }
        AppButtonVariant.OUTLINE -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, SurfaceBorderDark),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary
                ),
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                content = content
            )
        }
        AppButtonVariant.DANGER -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF1744),
                    contentColor = Color.White
                ),
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                content = content
            )
        }
    }
}
