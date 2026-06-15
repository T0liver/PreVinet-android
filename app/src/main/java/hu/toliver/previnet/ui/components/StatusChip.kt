package hu.toliver.previnet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.toliver.previnet.R
import hu.toliver.previnet.data.db.ServerStatus
import hu.toliver.previnet.data.db.SubmissionState
import hu.toliver.previnet.ui.theme.Spacing

/** One place mapping local state + server status (+ connectivity) onto the status chip. */
@Composable
fun StatusChip(
    state: String,
    serverStatus: String?,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector
    val label: String
    val containerColor: Color
    val contentColor: Color
    var border: BorderStroke? = null

    when {
        state == SubmissionState.FAILED -> {
            icon = Icons.Rounded.Warning
            label = stringResource(R.string.status_failed)
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        state == SubmissionState.QUEUED && !isOnline -> {
            icon = Icons.Rounded.CloudOff
            label = stringResource(R.string.status_saved_offline)
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        state == SubmissionState.QUEUED || state == SubmissionState.UPLOADING -> {
            icon = Icons.Rounded.CloudUpload
            label = stringResource(R.string.status_uploading)
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        serverStatus == ServerStatus.SEGMENTED -> {
            icon = Icons.Rounded.CheckCircle
            label = stringResource(R.string.status_results_ready)
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        serverStatus == ServerStatus.DONE -> {
            icon = Icons.Rounded.Check
            label = stringResource(R.string.status_complete)
            containerColor = Color.Transparent
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
        else -> {
            icon = Icons.Rounded.HourglassEmpty
            label = stringResource(R.string.status_processing)
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.xs),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}
