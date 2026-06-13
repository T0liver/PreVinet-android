package com.previNet.android.ui.result

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.previNet.android.data.api.Bbox
import com.previNet.android.ui.theme.MaskMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the AI segmentation mask in magenta over the photo, plus the original
 * user-drawn bbox as a dashed amber outline. Magenta (#FF00FF) is the complementary
 * colour of leaf green and stays visible on foliage in direct sunlight — masks are
 * never green.
 */
@Composable
fun MaskOverlay(
    maskUrl: String?,
    originalBbox: Bbox?,
    maskAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Fetch the binary mask PNG (white = diseased) and tint it once per URL, off the main thread.
    val maskBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, key1 = maskUrl) {
        if (maskUrl == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(maskUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
                tintMask(result.image.toBitmap())
            }.getOrNull()
        }
    }

    val bboxColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        maskBitmap?.let { mask ->
            drawImage(
                image = mask,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(mask.width, mask.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                alpha = maskAlpha.coerceIn(0f, 1f),
            )
        }
        originalBbox?.let { bbox ->
            drawRect(
                color = bboxColor,
                topLeft = Offset(
                    (bbox.x * size.width).toFloat(),
                    (bbox.y * size.height).toFloat(),
                ),
                size = Size(
                    (bbox.w * size.width).toFloat(),
                    (bbox.h * size.height).toFloat(),
                ),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                ),
            )
        }
    }
}

/** White (luminance > 128) mask pixels become opaque magenta; everything else transparent. */
private fun tintMask(source: Bitmap): ImageBitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    val converted = if (source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        source.copy(Bitmap.Config.ARGB_8888, false)
    }
    converted.getPixels(pixels, 0, width, 0, 0, width, height)

    val magenta = MaskMagenta.toArgb()
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val luminance = (r * 299 + g * 587 + b * 114) / 1000
        pixels[i] = if (luminance > 128) magenta else 0x00000000
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    return output.asImageBitmap()
}
