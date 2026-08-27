package com.streamapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamapp.core.designsystem.theme.PillShape
import com.streamapp.core.designsystem.theme.StatusError
import com.streamapp.core.designsystem.theme.StatusOffline
import com.streamapp.core.designsystem.theme.StatusOnline
import com.streamapp.core.designsystem.theme.StatusWarning
import com.streamapp.core.designsystem.theme.SurfaceDark
import com.streamapp.core.designsystem.theme.TextPrimary
import com.streamapp.core.model.server.LatencyQuality

@Composable
fun LatencyIndicator(
    latencyMs: Long?,
    modifier: Modifier = Modifier
) {
    val quality = LatencyQuality.fromMs(latencyMs)
    val (color, label) = when (quality) {
        LatencyQuality.EXCELLENT -> StatusOnline to "${latencyMs}ms"
        LatencyQuality.GOOD -> StatusOnline.copy(alpha = 0.85f) to "${latencyMs}ms"
        LatencyQuality.AVERAGE -> StatusWarning to "${latencyMs}ms"
        LatencyQuality.POOR -> StatusError to "${latencyMs}ms"
        LatencyQuality.OFFLINE -> StatusOffline to "—"
    }

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(SurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, color.copy(alpha = 0.4f), PillShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}
