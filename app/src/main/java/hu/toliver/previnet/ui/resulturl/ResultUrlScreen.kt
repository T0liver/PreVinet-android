package hu.toliver.previnet.ui.resulturl

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.toliver.previnet.AppContainer
import hu.toliver.previnet.R
import hu.toliver.previnet.appContainer
import hu.toliver.previnet.data.NotifChoice
import hu.toliver.previnet.data.db.SubmissionState
import hu.toliver.previnet.data.db.SubmissionWithPhotos
import hu.toliver.previnet.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ResultUrlViewModel(
    private val container: AppContainer,
    localId: Long,
) : ViewModel() {

    val row: StateFlow<SubmissionWithPhotos?> = container.submissions.observe(localId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notifChoice: StateFlow<NotifChoice> = container.prefs.notifChoice
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotifChoice.UNASKED)

    fun setNotifChoice(choice: NotifChoice) {
        viewModelScope.launch { container.prefs.setNotifChoice(choice) }
    }

    companion object {
        fun factory(container: AppContainer, localId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { ResultUrlViewModel(container, localId) } }
    }
}

@Composable
fun ResultUrlScreen(
    localId: Long,
    onOpenResults: () -> Unit,
    onViewSubmissions: () -> Unit,
    onSubmitAnother: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ResultUrlViewModel = viewModel(
        factory = ResultUrlViewModel.factory(context.appContainer, localId),
    )
    val row by viewModel.row.collectAsStateWithLifecycle()
    val notifChoice by viewModel.notifChoice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val submission = row?.submission
            val photoCount = row?.photos?.size ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(Spacing.md),
            ) {
                when {
                    submission == null -> Unit
                    submission.state == SubmissionState.SUBMITTED && submission.resultUrl != null -> {
                        UploadedVariant(
                            resultUrl = submission.resultUrl,
                            photoCount = photoCount,
                            notifChoice = notifChoice,
                            onNotifChoice = viewModel::setNotifChoice,
                            snackbarHostState = snackbarHostState,
                            onOpenResults = onOpenResults,
                            onSubmitAnother = onSubmitAnother,
                        )
                    }
                    else -> {
                        SavedOfflineVariant(
                            photoCount = photoCount,
                            notifChoice = notifChoice,
                            onNotifChoice = viewModel::setNotifChoice,
                            onViewSubmissions = onViewSubmissions,
                            onSubmitAnother = onSubmitAnother,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadedVariant(
    resultUrl: String,
    photoCount: Int,
    notifChoice: NotifChoice,
    onNotifChoice: (NotifChoice) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenResults: () -> Unit,
    onSubmitAnother: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val linkCopiedMessage = stringResource(R.string.link_copied)

    Spacer(Modifier.height(Spacing.lg))
    Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.submitted_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = pluralStringResource(R.plurals.photos_received, photoCount, photoCount),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.lg))
    HorizontalDivider()
    Spacer(Modifier.height(Spacing.lg))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(R.string.save_link_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.save_link_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.md))

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = resultUrl.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
    Spacer(Modifier.height(Spacing.md))

    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(resultUrl))
            copied = true
            scope.launch {
                snackbarHostState.showSnackbar(linkCopiedMessage)
            }
            scope.launch {
                delay(2_000)
                copied = false
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
        Text(
            text = if (copied) stringResource(R.string.copied) else stringResource(R.string.copy_link),
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    OutlinedButton(
        onClick = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, resultUrl)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null)
        Text(
            text = stringResource(R.string.share),
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }

    Spacer(Modifier.height(Spacing.lg))
    HorizontalDivider()
    Spacer(Modifier.height(Spacing.lg))

    if (notifChoice == NotifChoice.UNASKED) {
        NotificationPrePrompt(onNotifChoice = onNotifChoice)
        Spacer(Modifier.height(Spacing.md))
    }

    Text(
        text = stringResource(R.string.results_ready_soon),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
    Button(
        onClick = onOpenResults,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(stringResource(R.string.open_results))
    }
    TextButton(onClick = onSubmitAnother, modifier = Modifier.padding(top = Spacing.sm)) {
        Text(stringResource(R.string.submit_another))
    }
}

@Composable
private fun SavedOfflineVariant(
    photoCount: Int,
    notifChoice: NotifChoice,
    onNotifChoice: (NotifChoice) -> Unit,
    onViewSubmissions: () -> Unit,
    onSubmitAnother: () -> Unit,
) {
    val context = LocalContext.current
    val notificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Spacer(Modifier.height(Spacing.lg))
    Icon(
        imageVector = Icons.Rounded.CloudUpload,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.saved_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = pluralStringResource(R.plurals.saved_body, photoCount, photoCount),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.md))

    if (notificationsEnabled && notifChoice != NotifChoice.REFUSED) {
        Text(
            text = stringResource(R.string.saved_notify),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else if (notifChoice == NotifChoice.UNASKED) {
        NotificationPrePrompt(onNotifChoice = onNotifChoice)
    }

    Spacer(Modifier.height(Spacing.lg))
    Button(
        onClick = onViewSubmissions,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(stringResource(R.string.view_my_submissions))
    }
    TextButton(onClick = onSubmitAnother, modifier = Modifier.padding(top = Spacing.sm)) {
        Text(stringResource(R.string.submit_another))
    }
}

/** Pre-prompt before the system POST_NOTIFICATIONS dialog; a refusal is never re-asked. */
@Composable
private fun NotificationPrePrompt(onNotifChoice: (NotifChoice) -> Unit) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onNotifChoice(if (granted) NotifChoice.ACCEPTED else NotifChoice.REFUSED)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.notif_preprompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onNotifChoice(NotifChoice.ACCEPTED)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.notif_yes))
                }
                Spacer(Modifier.size(Spacing.sm))
                TextButton(
                    onClick = { onNotifChoice(NotifChoice.REFUSED) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.notif_no))
                }
            }
        }
    }
}
