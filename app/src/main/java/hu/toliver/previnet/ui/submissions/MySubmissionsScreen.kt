package hu.toliver.previnet.ui.submissions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import hu.toliver.previnet.AppContainer
import hu.toliver.previnet.R
import hu.toliver.previnet.appContainer
import hu.toliver.previnet.data.db.SubmissionState
import hu.toliver.previnet.data.db.SubmissionWithPhotos
import hu.toliver.previnet.ui.components.StatusChip
import hu.toliver.previnet.ui.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

class MySubmissionsViewModel(private val container: AppContainer) : ViewModel() {

    val submissions: StateFlow<List<SubmissionWithPhotos>> = container.submissions.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isOnline: StateFlow<Boolean> = container.connectivity.isOnline

    init {
        // Refresh server status for everything uploaded but not done; offline just
        // keeps showing the last known state.
        viewModelScope.launch { container.submissions.refreshAllStatuses() }
    }

    fun delete(localId: Long) {
        viewModelScope.launch { container.submissions.delete(localId) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { MySubmissionsViewModel(container) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubmissionsScreen(
    onBack: () -> Unit,
    onOpenSubmission: (Long) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MySubmissionsViewModel = viewModel(
        factory = MySubmissionsViewModel.factory(context.appContainer),
    )
    val submissions by viewModel.submissions.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<SubmissionWithPhotos?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_submissions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (submissions.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
            ) {
                items(submissions, key = { it.submission.localId }) { row ->
                    SubmissionCard(
                        row = row,
                        isOnline = isOnline,
                        onClick = { onOpenSubmission(row.submission.localId) },
                        onLongClick = { deleteTarget = row },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .padding(vertical = Spacing.sm / 2),
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_dialog_local))
                    if (target.submission.state == SubmissionState.SUBMITTED) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(stringResource(R.string.delete_dialog_link))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.submission.localId)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubmissionCard(
    row: SubmissionWithPhotos,
    isOnline: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submission = row.submission
    val photos = row.sortedPhotos
    val firstPhoto = photos.firstOrNull()?.filePath?.let { path ->
        File(path).takeIf { it.exists() }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            if (firstPhoto != null) {
                AsyncImage(
                    model = firstPhoto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Eco,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    text = "${pluralStringResource(R.plurals.photo_count, photos.size, photos.size)} · " +
                        stringResource(R.string.tier_chip, submission.annotationTier),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = dateFormatter.format(
                        Instant.ofEpochMilli(submission.createdAt).atZone(ZoneId.systemDefault())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (submission.state == SubmissionState.FAILED && !submission.lastError.isNullOrBlank()) {
                    Text(
                        text = submission.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            StatusChip(
                state = submission.state,
                serverStatus = submission.serverStatus,
                isOnline = isOnline,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
