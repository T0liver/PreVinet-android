package hu.toliver.previnet.ui.submit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import hu.toliver.previnet.R
import hu.toliver.previnet.appContainer
import hu.toliver.previnet.data.Disease
import hu.toliver.previnet.ui.components.OfflineBanner
import hu.toliver.previnet.ui.components.TierIndicator
import hu.toliver.previnet.ui.theme.Spacing
import hu.toliver.previnet.util.Haptics
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SubmitScreen(
    consentGiven: Boolean,
    onRequireConsent: () -> Unit,
    onNavigateToSaved: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SubmitViewModel = viewModel(factory = SubmitViewModel.factory(context.appContainer))

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val selectedDisease by viewModel.selectedDisease.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val shareGps by viewModel.shareGps.collectAsStateWithLifecycle()
    val optionalExpanded by viewModel.optionalExpanded.collectAsStateWithLifecycle()
    val importingCount by viewModel.importingCount.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val diseases by viewModel.diseases.collectAsStateWithLifecycle()
    val tier by viewModel.tier.collectAsStateWithLifecycle()
    val tutorialShown by viewModel.bboxTutorialShown.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToSaved.collect { onNavigateToSaved(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SubmitViewModel.MAX_PHOTOS),
    ) { uris -> viewModel.addUris(uris) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.onCameraResult(success) }

    val launchCamera = { cameraLauncher.launch(viewModel.prepareCameraUri()) }
    val launchGallery = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    var bboxSheetIndex by remember { mutableStateOf<Int?>(null) }
    var diseaseSheetOpen by remember { mutableStateOf(false) }

    val isUploading = uploadState is UploadUiState.Uploading

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OfflineBanner(visible = !isOnline)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(modifier = Modifier.widthIn(max = 600.dp)) {
                        SubmitHeader(pendingCount = pendingCount, onNavigateToHistory = onNavigateToHistory)

                        Box {
                            Column(modifier = Modifier.alpha(if (isUploading) 0.5f else 1f)) {
                                PhotosCard(
                                    photos = photos,
                                    importingCount = importingCount,
                                    onTakePhoto = launchCamera,
                                    onPickGallery = launchGallery,
                                    onRemovePhoto = viewModel::removePhoto,
                                    onOpenBboxSheet = { bboxSheetIndex = it },
                                )
                                Spacer(Modifier.height(Spacing.md))
                                AnnotationCard(
                                    diseases = diseases,
                                    selectedDisease = selectedDisease,
                                    tier = tier,
                                    showBboxHelper = selectedDisease != null &&
                                        photos.isNotEmpty() &&
                                        photos.none { it.bbox != null },
                                    onOpenDiseasePicker = { diseaseSheetOpen = true },
                                )
                                Spacer(Modifier.height(Spacing.md))
                                OptionalCard(
                                    expanded = optionalExpanded,
                                    notes = notes,
                                    shareGps = shareGps,
                                    onToggleExpanded = viewModel::toggleOptional,
                                    onNotesChange = viewModel::setNotes,
                                    onShareGpsChange = viewModel::setShareGps,
                                )
                            }
                            if (isUploading) {
                                // Block all interaction with the form while the upload runs.
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent().changes.forEach { it.consume() }
                                                }
                                            }
                                        },
                                )
                            }
                        }

                        Spacer(Modifier.height(Spacing.lg))
                        SubmitArea(
                            consentGiven = consentGiven,
                            photoCount = photos.size,
                            isOnline = isOnline,
                            uploadState = uploadState,
                            onSubmit = viewModel::submit,
                            onRequireConsent = onRequireConsent,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }

    if (diseaseSheetOpen) {
        DiseasePickerSheet(
            diseases = diseases,
            selected = selectedDisease,
            onSelect = {
                viewModel.selectDisease(it)
                diseaseSheetOpen = false
            },
            onDismiss = { diseaseSheetOpen = false },
        )
    }

    bboxSheetIndex?.let { startIndex ->
        if (photos.isNotEmpty()) {
            BboxDrawSheet(
                photos = photos,
                startIndex = startIndex.coerceIn(0, photos.size - 1),
                showTutorial = !tutorialShown,
                onTutorialShown = viewModel::markTutorialShown,
                onSetBbox = viewModel::setBbox,
                onDismiss = { bboxSheetIndex = null },
            )
        } else {
            bboxSheetIndex = null
        }
    }
}

@Composable
private fun SubmitHeader(pendingCount: Int, onNavigateToHistory: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.submit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNavigateToHistory) {
            BadgedBox(
                badge = {
                    if (pendingCount > 0) {
                        Badge { Text(pendingCount.toString()) }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = stringResource(R.string.history_cd),
                )
            }
        }
    }
}

@Composable
private fun PhotosCard(
    photos: List<DraftPhotoUi>,
    importingCount: Int,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onOpenBboxSheet: (Int) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (photos.isEmpty()) {
                EmptyPhotoBox(onTakePhoto = onTakePhoto, onPickGallery = onPickGallery)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    itemsIndexed(photos, key = { _, photo -> photo.file.absolutePath }) { index, photo ->
                        PhotoThumbnail(
                            photo = photo,
                            index = index,
                            onRemove = { onRemovePhoto(index) },
                            onClick = { onOpenBboxSheet(index) },
                        )
                    }
                    item {
                        AddMoreTile(onTakePhoto = onTakePhoto, onPickGallery = onPickGallery)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(
                        R.string.photos_meta,
                        pluralStringResource(R.plurals.photo_count, photos.size, photos.size),
                        formatSize(photos.sumOf { it.sizeBytes }),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (importingCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = pluralStringResource(R.plurals.adding_photos, importingCount, importingCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPhotoBox(onTakePhoto: () -> Unit, onPickGallery: () -> Unit) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .drawBehind {
                drawRoundRect(
                    color = outlineColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                    ),
                )
            }
            .padding(Spacing.md),
    ) {
        FilledTonalButton(
            onClick = onTakePhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
            Text(
                text = stringResource(R.string.take_photo),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = onPickGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
            Text(
                text = stringResource(R.string.add_from_gallery),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: DraftPhotoUi,
    index: Int,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val hasBbox = photo.bbox != null
    val thumbnailCd = if (hasBbox) {
        stringResource(R.string.photo_thumb_box_cd, index + 1)
    } else {
        stringResource(R.string.photo_thumb_nobox_cd, index + 1)
    }
    Box(modifier = Modifier.size(88.dp)) {
        AsyncImage(
            model = photo.file,
            contentDescription = thumbnailCd,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (hasBbox) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium,
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.remove_photo_cd, index + 1),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        if (hasBbox) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.xs)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddMoreTile(onTakePhoto: () -> Unit, onPickGallery: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val outlineColor = MaterialTheme.colorScheme.outline
    Box {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(88.dp)
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                        ),
                    )
                }
                .clip(MaterialTheme.shapes.medium)
                .clickable { menuOpen = true },
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.add_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.take_photo)) },
                leadingIcon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_from_gallery)) },
                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onPickGallery()
                },
            )
        }
    }
}

@Composable
private fun AnnotationCard(
    diseases: List<Disease>,
    selectedDisease: Disease?,
    tier: Int,
    showBboxHelper: Boolean,
    onOpenDiseasePicker: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable(onClick = onOpenDiseasePicker)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFlorist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = selectedDisease?.let { diseaseDisplayName(it) }
                        ?: stringResource(R.string.select_disease_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedDisease != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm + Spacing.xs),
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            TierIndicator(tier = tier)

            if (showBboxHelper) {
                Text(
                    text = stringResource(R.string.bbox_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun OptionalCard(
    expanded: Boolean,
    notes: String,
    shareGps: Boolean,
    onToggleExpanded: () -> Unit,
    onNotesChange: (String) -> Unit,
    onShareGpsChange: (Boolean) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.optional_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(R.string.notes_label)) },
                        supportingText = {
                            Text(
                                text = stringResource(R.string.notes_counter, notes.length),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.gps_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.gps_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = shareGps,
                            onCheckedChange = onShareGpsChange,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmitArea(
    consentGiven: Boolean,
    photoCount: Int,
    isOnline: Boolean,
    uploadState: UploadUiState,
    onSubmit: () -> Unit,
    onRequireConsent: () -> Unit,
) {
    val view = LocalView.current

    when (uploadState) {
        is UploadUiState.Uploading -> {
            var slowHint by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(8_000)
                slowHint = true
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { uploadState.progress },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = pluralStringResource(
                        R.plurals.uploading_progress,
                        photoCount,
                        photoCount,
                        (uploadState.progress * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (slowHint) {
                    Text(
                        text = stringResource(R.string.uploading_slow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        is UploadUiState.Error -> {
            LaunchedEffect(uploadState) { Haptics.reject(view) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { 1f },
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = uploadState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                SubmitButton(
                    consentGiven = consentGiven,
                    photoCount = photoCount,
                    isOnline = isOnline,
                    onSubmit = onSubmit,
                    onRequireConsent = onRequireConsent,
                )
            }
        }

        UploadUiState.Idle -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SubmitButton(
                    consentGiven = consentGiven,
                    photoCount = photoCount,
                    isOnline = isOnline,
                    onSubmit = onSubmit,
                    onRequireConsent = onRequireConsent,
                )
                if (photoCount == 0) {
                    Text(
                        text = stringResource(R.string.submit_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmitButton(
    consentGiven: Boolean,
    photoCount: Int,
    isOnline: Boolean,
    onSubmit: () -> Unit,
    onRequireConsent: () -> Unit,
) {
    val view = LocalView.current

    if (!consentGiven) {
        OutlinedButton(
            onClick = onRequireConsent,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.review_data_notice))
        }
        return
    }

    Button(
        onClick = {
            Haptics.confirm(view)
            onSubmit()
        },
        enabled = photoCount > 0,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (photoCount > 0) 1f else 0.38f),
    ) {
        if (!isOnline) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
            Spacer(Modifier.padding(start = Spacing.sm))
        }
        Text(
            text = if (isOnline) {
                pluralStringResource(R.plurals.submit_online, photoCount.coerceAtLeast(1), photoCount)
            } else {
                pluralStringResource(R.plurals.submit_offline, photoCount.coerceAtLeast(1), photoCount)
            },
        )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
    }
}
