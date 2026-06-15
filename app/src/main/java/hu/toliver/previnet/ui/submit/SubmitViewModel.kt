package hu.toliver.previnet.ui.submit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.toliver.previnet.AppContainer
import hu.toliver.previnet.data.Disease
import hu.toliver.previnet.data.DraftPhoto
import hu.toliver.previnet.data.SubmissionDraft
import hu.toliver.previnet.data.api.Bbox
import hu.toliver.previnet.data.db.SubmissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.launch
import java.io.File

data class DraftPhotoUi(
    val file: File,
    val sizeBytes: Long,
    val lat: Double?,
    val lon: Double?,
    val bbox: Bbox?,
)

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data class Uploading(val progress: Float) : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class SubmitViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.submissions

    private val _photos = MutableStateFlow<List<DraftPhotoUi>>(emptyList())
    val photos: StateFlow<List<DraftPhotoUi>> = _photos.asStateFlow()

    private val _selectedDisease = MutableStateFlow<Disease?>(null)
    val selectedDisease: StateFlow<Disease?> = _selectedDisease.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _shareGps = MutableStateFlow(true)
    val shareGps: StateFlow<Boolean> = _shareGps.asStateFlow()

    private val _optionalExpanded = MutableStateFlow(false)
    val optionalExpanded: StateFlow<Boolean> = _optionalExpanded.asStateFlow()

    private val _importingCount = MutableStateFlow(0)
    val importingCount: StateFlow<Int> = _importingCount.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _navigateToSaved = MutableSharedFlow<Long>()
    val navigateToSaved: SharedFlow<Long> = _navigateToSaved.asSharedFlow()

    val isOnline: StateFlow<Boolean> = container.connectivity.isOnline

    val pendingCount: StateFlow<Int> = repo.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val diseases: StateFlow<List<Disease>> = container.diseases.diseases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bboxTutorialShown: StateFlow<Boolean> = container.prefs.bboxTutorialShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val tier: StateFlow<Int> = combine(_photos, _selectedDisease) { photos, disease ->
        when {
            disease != null && photos.any { it.bbox != null } -> 3
            disease != null -> 2
            else -> 1
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private var pendingCamera: Pair<Uri, File>? = null
    private var watchJob: Job? = null

    /** When the last online submit failed permanently, resubmitting retries that row. */
    private var failedLocalId: Long? = null

    fun prepareCameraUri(): Uri {
        val pair = container.imageStore.newCameraOutputUri()
        pendingCamera = pair
        return pair.first
    }

    fun onCameraResult(success: Boolean) {
        val (uri, file) = pendingCamera ?: return
        pendingCamera = null
        if (success) {
            addUris(listOf(uri), tempFile = file)
        } else {
            file.delete()
        }
    }

    fun addUris(uris: List<Uri>, tempFile: File? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val room = (MAX_PHOTOS - _photos.value.size).coerceAtLeast(0)
            val toImport = uris.take(room)
            _importingCount.value = toImport.size
            try {
                toImport.forEach { uri ->
                    runCatching { container.imageStore.import(uri, stripGps = !_shareGps.value) }
                        .onSuccess { imported ->
                            _photos.update {
                                it + DraftPhotoUi(
                                    file = imported.file,
                                    sizeBytes = imported.sizeBytes,
                                    lat = imported.lat,
                                    lon = imported.lon,
                                    bbox = null,
                                )
                            }
                        }
                        .onFailure { Log.e("SubmitViewModel", "Failed to import $uri", it) }
                    _importingCount.update { (it - 1).coerceAtLeast(0) }
                }
            } finally {
                _importingCount.value = 0
                tempFile?.delete()
            }
        }
    }

    fun removePhoto(index: Int) {
        val photo = _photos.value.getOrNull(index) ?: return
        _photos.update { it.filterIndexed { i, _ -> i != index } }
        viewModelScope.launch { container.imageStore.delete(listOf(photo.file.absolutePath)) }
        onFormEdited()
    }

    fun setBbox(index: Int, bbox: Bbox?) {
        _photos.update { list ->
            list.mapIndexed { i, photo -> if (i == index) photo.copy(bbox = bbox) else photo }
        }
        onFormEdited()
    }

    fun selectDisease(disease: Disease) {
        _selectedDisease.value = disease
        onFormEdited()
    }

    /** Editing after a permanent failure abandons the retry of that row. */
    private fun onFormEdited() {
        failedLocalId = null
    }

    fun setNotes(value: String) {
        _notes.value = value.take(500)
    }

    fun setShareGps(value: Boolean) {
        _shareGps.value = value
    }

    fun toggleOptional() {
        _optionalExpanded.update { !it }
    }

    fun markTutorialShown() {
        viewModelScope.launch { container.prefs.setBboxTutorialShown() }
    }

    fun submit() {
        if (_photos.value.isEmpty() || _uploadState.value is UploadUiState.Uploading) return
        viewModelScope.launch {
            val online = isOnline.value
            val retryId = failedLocalId
            val localId = if (retryId != null) {
                repo.retry(retryId)
                retryId
            } else {
                repo.enqueue(
                    SubmissionDraft(
                        photos = _photos.value.map {
                            DraftPhoto(it.file, it.sizeBytes, it.lat, it.lon, it.bbox)
                        },
                        diseaseLabel = _selectedDisease.value?.slug,
                        notes = _notes.value.takeIf { it.isNotBlank() },
                        shareGps = _shareGps.value,
                    )
                )
            }
            if (!online) {
                finishAndNavigate(localId)
            } else {
                _uploadState.value = UploadUiState.Uploading(0f)
                watchUpload(localId)
            }
        }
    }

    private fun watchUpload(localId: Long) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            combine(repo.observe(localId), repo.uploadProgress(localId)) { row, pct -> row to pct }
                .collect { (row, pct) ->
                    val submission = row?.submission ?: return@collect
                    when (submission.state) {
                        SubmissionState.SUBMITTED -> {
                            finishAndNavigate(localId)
                            watchJob?.cancel()
                        }
                        SubmissionState.FAILED -> {
                            failedLocalId = localId
                            _uploadState.value = UploadUiState.Error(submission.lastError.orEmpty())
                            watchJob?.cancel()
                        }
                        else -> {
                            if (!isOnline.value) {
                                // Connectivity dropped mid-upload: WorkManager owns it now.
                                finishAndNavigate(localId)
                                watchJob?.cancel()
                            } else {
                                _uploadState.value = UploadUiState.Uploading(pct)
                            }
                        }
                    }
                }
        }
    }

    private suspend fun finishAndNavigate(localId: Long) {
        resetForm()
        _navigateToSaved.emit(localId)
    }

    private fun resetForm() {
        // Files now belong to the Room submission (they are the local result cache) — keep them.
        _photos.value = emptyList()
        _selectedDisease.value = null
        _notes.value = ""
        _shareGps.value = true
        _optionalExpanded.value = false
        _uploadState.value = UploadUiState.Idle
        failedLocalId = null
    }

    companion object {
        const val MAX_PHOTOS = 30

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SubmitViewModel(container) }
        }
    }
}
