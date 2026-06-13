package com.previNet.android.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.previNet.android.R
import com.previNet.android.data.api.Bbox
import com.previNet.android.ui.components.BboxCanvas
import com.previNet.android.ui.components.rememberBboxDrawState
import com.previNet.android.ui.theme.SheetShape
import com.previNet.android.ui.theme.Spacing
import com.previNet.android.util.Haptics
import kotlin.math.min

/**
 * Correction drawing sheet: the magenta mask is shown faded under the drawing layer
 * and the user draws the correct box with the same gesture as at submit time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    photoModel: Any?,
    maskUrl: String?,
    saving: Boolean,
    onSubmit: (Bbox) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val areaHeight = (configuration.screenHeightDp * 0.55f).dp
    var aspectRatio by remember { mutableFloatStateOf(4f / 3f) }
    val drawState = rememberBboxDrawState(key = photoModel, initial = null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.correction_instruction),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Spacing.sm))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(areaHeight),
            ) {
                val density = LocalDensity.current
                val maxWidthPx = constraints.maxWidth.toFloat()
                val maxHeightPx = constraints.maxHeight.toFloat()
                val fittedWidthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
                val fittedHeightPx = fittedWidthPx / aspectRatio
                val fittedWidth = with(density) { fittedWidthPx.toDp() }
                val fittedHeight = with(density) { fittedHeightPx.toDp() }

                Box(
                    modifier = Modifier
                        .size(fittedWidth, fittedHeight)
                        .align(Alignment.Center),
                ) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                val img = state.result.image
                                if (img.width > 0 && img.height > 0) {
                                    aspectRatio = img.width.toFloat() / img.height
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The AI mask, faded right down so the user's drawing reads on top.
                    MaskOverlay(
                        maskUrl = maskUrl,
                        originalBbox = null,
                        maskAlpha = 0.15f,
                        modifier = Modifier.matchParentSize(),
                    )
                    val canvasCd = if (drawState.committed != null) {
                        stringResource(R.string.bbox_canvas_cd_drawn)
                    } else {
                        stringResource(R.string.bbox_canvas_cd_none)
                    }
                    BboxCanvas(
                        state = drawState,
                        onBboxCommitted = { Haptics.tick(view) },
                        modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = canvasCd },
                    )
                }
            }

            if (drawState.tooSmall) {
                Text(
                    text = stringResource(R.string.bbox_too_small),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { drawState.clear() },
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.clear))
                }
                Button(
                    onClick = { drawState.committed?.let(onSubmit) },
                    enabled = drawState.committed != null && !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.saving),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    } else {
                        Text(stringResource(R.string.submit_correction))
                    }
                }
            }
        }
    }
}
