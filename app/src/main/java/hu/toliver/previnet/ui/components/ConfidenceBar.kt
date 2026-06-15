package hu.toliver.previnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hu.toliver.previnet.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Full-width 8dp rounded confidence track: primary ≥ 0.7, secondary 0.4–0.69,
 * error < 0.4, percentage text to the right.
 */
@Composable
fun ConfidenceBar(confidence: Float, modifier: Modifier = Modifier) {
    val fillColor = when {
        confidence >= 0.7f -> MaterialTheme.colorScheme.primary
        confidence >= 0.4f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(fillColor),
            )
        }
        Text(
            text = "${(confidence * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}
