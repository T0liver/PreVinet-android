package hu.toliver.previnet.ui.submit

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import hu.toliver.previnet.R
import hu.toliver.previnet.data.api.Bbox
import hu.toliver.previnet.ui.components.BboxCanvas
import hu.toliver.previnet.ui.components.BboxDrawState
import hu.toliver.previnet.ui.components.rememberBboxDrawState
import hu.toliver.previnet.ui.theme.SheetShape
import hu.toliver.previnet.ui.theme.Spacing
import hu.toliver.previnet.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BboxDrawSheet(
    photos: List<DraftPhotoUi>,
    startIndex: Int,
    showTutorial: Boolean,
    onTutorialShown: () -> Unit,
    onSetBbox: (Int, Bbox?) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex) }
    val photo = photos.getOrNull(index)
    if (photo == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val view = LocalView.current

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
                text = stringResource(R.string.bbox_photo_index, index + 1, photos.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.bbox_instruction),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Spacing.sm))

            val drawState = rememberBboxDrawState(
                key = photo.file.absolutePath,
                initial = photo.bbox,
            )
            val canvasCd = if (drawState.committed != null) {
                stringResource(R.string.bbox_canvas_cd_drawn)
            } else {
                stringResource(R.string.bbox_canvas_cd_none)
            }

            PhotoDrawArea(
                file = photo.file,
                drawState = drawState,
                canvasContentDescription = canvasCd,
                showTutorial = showTutorial,
                onTutorialShown = onTutorialShown,
                onBboxCommitted = { bbox ->
                    Haptics.tick(view)
                    onSetBbox(index, bbox)
                },
            )

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
                    onClick = {
                        drawState.clear()
                        onSetBbox(index, null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.bbox_clear))
                }
                Button(
                    onClick = {
                        if (index < photos.lastIndex) index++ else onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.bbox_confirm))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { if (index > 0) index-- },
                    enabled = index > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.bbox_previous),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { if (index < photos.lastIndex) index++ },
                    enabled = index < photos.lastIndex,
                ) {
                    Text(
                        text = stringResource(R.string.bbox_next),
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Letterboxes the photo into the available area and overlays the drawing canvas on
 * exactly the rendered image bounds, so normalized bbox coordinates are image-relative.
 */
@Composable
private fun PhotoDrawArea(
    file: File,
    drawState: BboxDrawState,
    canvasContentDescription: String,
    showTutorial: Boolean,
    onTutorialShown: () -> Unit,
    onBboxCommitted: (Bbox) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val areaHeight = (configuration.screenHeightDp * 0.55f).dp

    val aspectRatio by produceState(initialValue = 4f / 3f, key1 = file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() / options.outHeight
            } else {
                4f / 3f
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(areaHeight),
    ) scope@{
        val density = LocalDensity.current
        val maxWidthPx = this@scope.constraints.maxWidth.toFloat()
        val maxHeightPx = this@scope.constraints.maxHeight.toFloat()
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
                model = file,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            BboxCanvas(
                state = drawState,
                onBboxCommitted = onBboxCommitted,
                modifier = Modifier
                    .matchParentSize()
                    .semantics { contentDescription = canvasContentDescription },
            )
            if (showTutorial) {
                TutorialGhost(
                    onDone = onTutorialShown,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/** First-open hint: a pulsing dot travels diagonally for 1.5 s, suggesting press-and-drag. */
@Composable
private fun TutorialGhost(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1_500, easing = LinearEasing))
        onDone()
    }
    val ghostColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val p = progress.value
        if (p < 1f) {
            val center = Offset(
                lerp(0.25f, 0.75f, p) * size.width,
                lerp(0.25f, 0.75f, p) * size.height,
            )
            val pulse = 1f + 0.3f * sin(p * 6f * PI.toFloat())
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                radius = 12.dp.toPx() * pulse,
                center = center,
            )
            drawCircle(
                color = ghostColor.copy(alpha = 0.8f),
                radius = 7.dp.toPx() * pulse,
                center = center,
            )
        }
    }
}
