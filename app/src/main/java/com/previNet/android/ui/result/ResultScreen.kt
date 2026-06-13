package com.previNet.android.ui.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.previNet.android.R
import com.previNet.android.appContainer
import com.previNet.android.data.db.ServerStatus
import com.previNet.android.data.db.SubmissionEntity
import com.previNet.android.data.db.SubmissionState
import com.previNet.android.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    localId: Long,
    onNewSubmission: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ResultViewModel = viewModel(
        factory = ResultViewModel.factory(context.appContainer, localId),
    )

    val row by viewModel.row.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val diseases by viewModel.diseases.collectAsStateWithLifecycle()
    val confirmedIds by viewModel.confirmedImageIds.collectAsStateWithLifecycle()
    val correctedIds by viewModel.correctedImageIds.collectAsStateWithLifecycle()
    val correctionSheetFor by viewModel.correctionSheetFor.collectAsStateWithLifecycle()
    val correctionSaving by viewModel.correctionSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val correctionSavedMessage = stringResource(R.string.correction_saved)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ResultEvent.CorrectionSaved -> snackbarHostState.showSnackbar(correctionSavedMessage)
                is ResultEvent.CorrectionFailed ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    // Poll every 60 s while the submission is still processing, only when STARTED.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000)
                val submission = viewModel.row.value?.submission
                val status = submission?.serverStatus
                val stillProcessing = submission?.serverId != null &&
                    !viewModel.notFound.value &&
                    (status == null || status == ServerStatus.PENDING || status == ServerStatus.RELAYED)
                if (stillProcessing) viewModel.refresh()
            }
        }
    }

    var deleteDialogOpen by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = checking,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val submission = row?.submission
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
            ) {
                item {
                    ResultHeader(
                        submission = submission,
                        photoCount = row?.photos?.size ?: 0,
                        onNewSubmission = onNewSubmission,
                    )
                }

                when {
                    submission == null -> Unit

                    notFound -> item { NotFoundContent() }

                    submission.serverId == null && submission.state == SubmissionState.FAILED -> item {
                        FailedContent(
                            error = submission.lastError,
                            onRetry = viewModel::uploadNow,
                            onDelete = { deleteDialogOpen = true },
                        )
                    }

                    submission.serverId == null -> item {
                        QueuedContent(isOnline = isOnline, onUploadNow = viewModel::uploadNow)
                    }

                    result != null && (
                        submission.serverStatus == ServerStatus.SEGMENTED ||
                            submission.serverStatus == ServerStatus.DONE
                        ) -> {
                        val images = result?.images.orEmpty()
                        val photos = row?.sortedPhotos.orEmpty()
                        items(images.size) { index ->
                            val image = images[index]
                            ResultCard(
                                image = image,
                                localPhoto = photos.getOrNull(index),
                                diseases = diseases,
                                absoluteUrl = context.appContainer.api::absoluteUrl,
                                confirmed = confirmedIds.contains(image.imageId),
                                corrected = image.corrected || correctedIds.contains(image.imageId),
                                onConfirm = { viewModel.confirmLooksGood(image.imageId) },
                                onCorrect = { viewModel.openCorrection(image.imageId) },
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .padding(bottom = Spacing.md),
                            )
                        }
                        item {
                            Text(
                                text = stringResource(R.string.thanks_footer),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = Spacing.lg),
                            )
                        }
                    }

                    else -> item {
                        ProcessingContent(
                            submission = submission,
                            photoCount = row?.photos?.size ?: 0,
                            checking = checking,
                            onCheck = viewModel::refresh,
                        )
                    }
                }
            }
        }
    }

    correctionSheetFor?.let { imageId ->
        val images = result?.images.orEmpty()
        val index = images.indexOfFirst { it.imageId == imageId }
        val image = images.getOrNull(index)
        if (image != null) {
            val localPhoto = row?.sortedPhotos?.getOrNull(index)
            val localFile = localPhoto?.filePath?.let { path -> File(path).takeIf { it.exists() } }
            val absoluteUrl = context.appContainer.api::absoluteUrl
            CorrectionSheet(
                photoModel = localFile ?: image.thumbnailUrl?.let(absoluteUrl),
                maskUrl = image.maskUrl?.let(absoluteUrl),
                saving = correctionSaving,
                onSubmit = { bbox -> viewModel.submitCorrection(imageId, bbox) },
                onDismiss = viewModel::closeCorrection,
            )
        }
    }

    if (deleteDialogOpen) {
        val submission = row?.submission
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_dialog_local))
                    if (submission?.state == SubmissionState.SUBMITTED) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(stringResource(R.string.delete_dialog_link))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        viewModel.delete(onDeleted = onNewSubmission)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ResultHeader(
    submission: SubmissionEntity?,
    photoCount: Int,
    onNewSubmission: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)) {
        TextButton(onClick = onNewSubmission) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.new_submission),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = stringResource(R.string.result_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        submission?.serverId?.let { serverId ->
            Text(
                text = serverId.let { if (it.length > 14) it.take(14) + "…" else it },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (photoCount > 0 && submission != null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.photo_count, photoCount, photoCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Spacing.sm))
                TierChip(tier = submission.annotationTier)
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun TierChip(tier: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.tier_chip, tier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun QueuedContent(isOnline: Boolean, onUploadNow: () -> Unit) {
    StateColumn {
        Icon(
            imageVector = Icons.Rounded.CloudUpload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.waiting_upload),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.waiting_upload_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (isOnline) {
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = onUploadNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.upload_now))
            }
        }
    }
}

@Composable
private fun FailedContent(
    error: String?,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    StateColumn {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.upload_failed),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.try_again))
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.delete),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProcessingContent(
    submission: SubmissionEntity,
    photoCount: Int,
    checking: Boolean,
    onCheck: () -> Unit,
) {
    StateColumn {
        Icon(
            imageVector = Icons.Rounded.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.processing_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.processing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = dateFormatter.format(
                Instant.ofEpochMilli(submission.createdAt).atZone(ZoneId.systemDefault())
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (photoCount > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.photo_count, photoCount, photoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Spacing.sm))
                TierChip(tier = submission.annotationTier)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        OutlinedButton(
            onClick = onCheck,
            enabled = !checking,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(Spacing.sm))
            }
            Text(stringResource(R.string.check_results))
        }
    }
}

@Composable
private fun NotFoundContent() {
    StateColumn {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.not_found_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.not_found_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StateColumn(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
    ) {
        content()
    }
}
