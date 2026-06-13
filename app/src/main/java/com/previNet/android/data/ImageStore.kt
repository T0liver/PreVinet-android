package com.previNet.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class ImportedPhoto(
    val file: File,
    val sizeBytes: Long,
    val lat: Double?,
    val lon: Double?,
)

/**
 * Owns the on-device photo cache: imported photos live in filesDir/photos and are
 * kept after upload so the result screen can render the local full-resolution file.
 */
class ImageStore(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    private val cameraDir: File
        get() = File(context.cacheDir, "camera").apply { mkdirs() }

    /**
     * Copies a picked/captured image into app storage: downscaled to a long edge of
     * [MAX_LONG_EDGE] px, EXIF rotation baked into the pixels, re-encoded as JPEG q85.
     * Re-encoding strips all metadata; GPS is read from EXIF beforehand and only
     * returned in memory (never written to the stored copy).
     */
    suspend fun import(uri: Uri, stripGps: Boolean): ImportedPhoto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Pass 1: bounds for inSampleSize.
        // Note: decodeStream with inJustDecodeBounds always returns null, so the null check must
        // be on the stream itself, not on the decodeStream result.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: throw IOException("Cannot open image"))
            .use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a decodable image")

        // Pass 2: EXIF (GPS + orientation) before the pixels are re-encoded.
        var lat: Double? = null
        var lon: Double? = null
        var orientation = ExifInterface.ORIENTATION_NORMAL
        resolver.openInputStream(uri)?.use { stream ->
            try {
                val exif = ExifInterface(stream)
                exif.latLong?.let { lat = it[0]; lon = it[1] }
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } catch (_: Exception) {
                // Missing/corrupt EXIF is fine — treat as no GPS, no rotation.
            }
        }

        // Pass 3: decode with subsampling, then scale precisely to the long-edge cap.
        var sampleSize = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sampleSize * 2) >= MAX_LONG_EDGE) sampleSize *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IOException("Cannot decode image")

        val decodedLongEdge = maxOf(bitmap.width, bitmap.height)
        if (decodedLongEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / decodedLongEdge
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        // Bake EXIF rotation in so bbox coordinates always refer to the displayed orientation.
        rotationMatrix(orientation)?.let { matrix ->
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }

        val outFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        try {
            outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } finally {
            bitmap.recycle()
        }

        ImportedPhoto(
            file = outFile,
            sizeBytes = outFile.length(),
            lat = if (stripGps) null else lat,
            lon = if (stripGps) null else lon,
        )
    }

    /** Output target for ACTION_IMAGE_CAPTURE via the FileProvider. */
    fun newCameraOutputUri(): Pair<Uri, File> {
        val file = File(cameraDir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    private fun rotationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return null
        }
        return matrix
    }

    companion object {
        const val MAX_LONG_EDGE = 2048
        const val JPEG_QUALITY = 85
    }
}
