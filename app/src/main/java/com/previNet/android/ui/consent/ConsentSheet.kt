package com.previNet.android.ui.consent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.previNet.android.R
import com.previNet.android.ui.theme.SheetShape
import com.previNet.android.ui.theme.Spacing

/**
 * Research data notice. The scrim/swipe cannot dismiss it; the system back gesture can
 * (the app stays browsable but submitting requires agreeing later).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSheet(
    onAgree: () -> Unit,
    onDismissWithoutAgreeing: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    ModalBottomSheet(
        onDismissRequest = onDismissWithoutAgreeing,
        sheetState = sheetState,
        shape = SheetShape,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.consent_sharing_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                ConsentBullet(stringResource(R.string.consent_item_photos))
                ConsentBullet(stringResource(R.string.consent_item_gps))
                ConsentBullet(stringResource(R.string.consent_item_ip))
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.consent_purpose),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.consent_agree))
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.consent_dismiss_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConsentBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}
