package com.previNet.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.previNet.android.data.api.Bbox
import com.previNet.android.ui.theme.BboxGreenFill
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Minimum area (normalized) for an accepted box. */
private const val MIN_BBOX_AREA = 0.05

@Stable
class BboxDrawState(initial: Bbox?) {
    /** The accepted box in normalized 0–1 image coordinates. */
    var committed: Bbox? by mutableStateOf(initial)

    var liveStart: Offset? by mutableStateOf(null)
        internal set
    var liveCurrent: Offset? by mutableStateOf(null)
        internal set

    /** Set when the last drawn box was discarded for being below the minimum area. */
    var tooSmall: Boolean by mutableStateOf(false)

    val isDragging: Boolean get() = liveStart != null && liveCurrent != null

    fun clear() {
        committed = null
        tooSmall = false
    }
}

/**
 * Drawing state shared by the submit-time bbox sheet and the result-correction sheet.
 * Keyed so navigating between photos resets the live gesture.
 */
@Composable
fun rememberBboxDrawState(key: Any?, initial: Bbox?): BboxDrawState =
    remember(key) { BboxDrawState(initial) }

/**
 * Press-and-drag rectangle overlay. The modifier must size this composable to exactly
 * the rendered image bounds so normalized coordinates are relative to the image.
 */
@Composable
fun BboxCanvas(
    state: BboxDrawState,
    onBboxCommitted: (Bbox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokeColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.pointerInput(state) {
            detectDragGestures(
                onDragStart = { offset ->
                    state.liveStart = clampToSize(offset, Size(size.width.toFloat(), size.height.toFloat()))
                    state.liveCurrent = state.liveStart
                    state.tooSmall = false
                },
                onDrag = { change, _ ->
                    change.consume()
                    state.liveCurrent =
                        clampToSize(change.position, Size(size.width.toFloat(), size.height.toFloat()))
                },
                onDragCancel = {
                    state.liveStart = null
                    state.liveCurrent = null
                },
                onDragEnd = {
                    val start = state.liveStart
                    val end = state.liveCurrent
                    state.liveStart = null
                    state.liveCurrent = null
                    if (start == null || end == null || size.width == 0 || size.height == 0) return@detectDragGestures
                    val bbox = Bbox(
                        x = (min(start.x, end.x) / size.width).toDouble().coerceIn(0.0, 1.0),
                        y = (min(start.y, end.y) / size.height).toDouble().coerceIn(0.0, 1.0),
                        w = (abs(end.x - start.x) / size.width).toDouble().coerceIn(0.0, 1.0),
                        h = (abs(end.y - start.y) / size.height).toDouble().coerceIn(0.0, 1.0),
                    )
                    if (bbox.w * bbox.h < MIN_BBOX_AREA) {
                        state.tooSmall = true
                    } else {
                        state.committed = bbox
                        state.tooSmall = false
                        onBboxCommitted(bbox)
                    }
                },
            )
        },
    ) {
        val rect: Rect? = if (state.isDragging) {
            val start = state.liveStart!!
            val current = state.liveCurrent!!
            Rect(
                Offset(min(start.x, current.x), min(start.y, current.y)),
                Offset(max(start.x, current.x), max(start.y, current.y)),
            )
        } else {
            state.committed?.let { bbox ->
                Rect(
                    Offset((bbox.x * size.width).toFloat(), (bbox.y * size.height).toFloat()),
                    Offset(
                        ((bbox.x + bbox.w) * size.width).toFloat(),
                        ((bbox.y + bbox.h) * size.height).toFloat(),
                    ),
                )
            }
        }

        rect?.let {
            drawRect(color = BboxGreenFill, topLeft = it.topLeft, size = it.size)
            drawRect(
                color = strokeColor,
                topLeft = it.topLeft,
                size = it.size,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun clampToSize(offset: Offset, size: Size): Offset =
    Offset(offset.x.coerceIn(0f, size.width), offset.y.coerceIn(0f, size.height))
