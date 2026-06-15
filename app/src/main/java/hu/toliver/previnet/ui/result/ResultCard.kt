package hu.toliver.previnet.ui.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import hu.toliver.previnet.R
import hu.toliver.previnet.data.Disease
import hu.toliver.previnet.data.api.Bbox
import hu.toliver.previnet.data.api.ResultImageDto
import hu.toliver.previnet.data.db.PhotoEntity
import hu.toliver.previnet.ui.components.ConfidenceBar
import hu.toliver.previnet.ui.theme.MASK_FILL_ALPHA
import hu.toliver.previnet.ui.theme.Spacing
import hu.toliver.previnet.util.Haptics
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ResultCard(
    image: ResultImageDto,
    localPhoto: PhotoEntity?,
    diseases: List<Disease>,
    absoluteUrl: (String) -> String,
    confirmed: Boolean,
    corrected: Boolean,
    onConfirm: () -> Unit,
    onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var sliderValue by remember { mutableFloatStateOf(0.75f) }

    // The cache rule: render the local full-res photo when this device took it;
    // only the mask PNG comes from the network. Fall back to the server thumbnail.
    val localFile = localPhoto?.filePath?.let { path ->
        File(path).takeIf { it.exists() }
    }
    val photoModel: Any? = localFile ?: image.thumbnailUrl?.let(absoluteUrl)
    val maskUrl = image.maskUrl?.let(absoluteUrl)
    val originalBbox = localPhoto?.let { photo ->
        if (photo.bboxX != null && photo.bboxY != null && photo.bboxW != null && photo.bboxH != null) {
            Bbox(photo.bboxX, photo.bboxY, photo.bboxW, photo.bboxH)
        } else {
            null
        }
    }

    var aspectRatio by remember { mutableFloatStateOf(4f / 3f) }

    val photoContentDesc = image.diseaseLabel
        ?.let { diseaseNameForSlug(it, diseases) }
        ?: stringResource(R.string.result_photo_cd)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
        ) {
            AsyncImage(
                model = photoModel,
                contentDescription = photoContentDesc,
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
            MaskOverlay(
                maskUrl = maskUrl,
                originalBbox = originalBbox,
                maskAlpha = MASK_FILL_ALPHA * sliderValue,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(modifier = Modifier.padding(Spacing.md)) {
            if (maskUrl == null) {
                Text(
                    text = stringResource(R.string.original_annotation_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mask_visibility),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.sm),
                    )
                    Text(
                        text = "${(sliderValue * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            image.diseaseLabel?.let { slug ->
                Text(
                    text = diseaseNameForSlug(slug, diseases),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            image.confidence?.let { confidence ->
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.confidence_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    ConfidenceBar(
                        confidence = confidence.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            image.qualityScore?.let { quality ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.quality_score_fmt, (quality * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.md))

            when {
                corrected -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.correction_submitted_chip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        )
                    }
                }
                confirmed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.thanks_confirming),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.looks_right),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row {
                        OutlinedButton(
                            onClick = {
                                Haptics.confirm(view)
                                onConfirm()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.looks_good),
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        FilledTonalButton(
                            onClick = onCorrect,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.correct_it),
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun diseaseNameForSlug(slug: String, diseases: List<Disease>): String {
    val match = diseases.firstOrNull { it.slug == slug }
    return when {
        match?.nameRes != null -> stringResource(match.nameRes)
        match != null -> match.fallbackName
        else -> slug.split('_', '-').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
