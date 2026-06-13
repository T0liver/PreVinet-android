package com.previNet.android.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.previNet.android.AppContainer
import com.previNet.android.R
import com.previNet.android.data.Disease
import com.previNet.android.data.SubmissionRepository
import com.previNet.android.data.api.ApiException
import com.previNet.android.data.api.Bbox
import com.previNet.android.data.api.SubmissionResultDto
import com.previNet.android.data.db.ServerStatus
import com.previNet.android.data.db.SubmissionWithPhotos
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResultEvent {
    data object CorrectionSaved : ResultEvent
    data class CorrectionFailed(val messageRes: Int) : ResultEvent
}

class ResultViewModel(
    private val container: AppContainer,
    private val localId: Long,
) : ViewModel() {

    private val repo = container.submissions

    val row: StateFlow<SubmissionWithPhotos?> = repo.observe(localId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isOnline: StateFlow<Boolean> = container.connectivity.isOnline

    val diseases: StateFlow<List<Disease>> = container.diseases.diseases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _result = MutableStateFlow<SubmissionResultDto?>(null)
    val result: StateFlow<SubmissionResultDto?> = _result.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Session-local "Looks good" confirmations, keyed by server image id. */
    private val _confirmedImageIds = MutableStateFlow<Set<String>>(emptySet())
    val confirmedImageIds: StateFlow<Set<String>> = _confirmedImageIds.asStateFlow()

    private val _correctedImageIds = MutableStateFlow<Set<String>>(emptySet())
    val correctedImageIds: StateFlow<Set<String>> = _correctedImageIds.asStateFlow()

    /** Server image id whose correction sheet is open, or null. */
    private val _correctionSheetFor = MutableStateFlow<String?>(null)
    val correctionSheetFor: StateFlow<String?> = _correctionSheetFor.asStateFlow()

    private val _correctionSaving = MutableStateFlow(false)
    val correctionSaving: StateFlow<Boolean> = _correctionSaving.asStateFlow()

    private val _events = MutableSharedFlow<ResultEvent>()
    val events: SharedFlow<ResultEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            try {
                val status = repo.refreshStatus(localId)
                when {
                    status == null -> Unit // not uploaded yet, or row missing
                    status == SubmissionRepository.STATUS_NOT_FOUND -> _notFound.value = true
                    else -> {
                        _notFound.value = false
                        if (status == ServerStatus.SEGMENTED || status == ServerStatus.DONE) {
                            _result.value = repo.fetchResult(localId)
                            // The user is looking at real results — no reminder needed.
                            repo.cancelReminder(localId)
                        }
                    }
                }
            } catch (_: ApiException) {
                // Offline or server hiccup: keep showing the last known state.
            } finally {
                _checking.value = false
            }
        }
    }

    fun uploadNow() {
        viewModelScope.launch { repo.retry(localId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.delete(localId)
            onDeleted()
        }
    }

    fun confirmLooksGood(imageId: String) {
        _confirmedImageIds.update { it + imageId }
    }

    fun openCorrection(imageId: String) {
        _correctionSheetFor.value = imageId
    }

    fun closeCorrection() {
        if (!_correctionSaving.value) _correctionSheetFor.value = null
    }

    fun submitCorrection(imageId: String, bbox: Bbox) {
        val serverId = row.value?.submission?.serverId ?: return
        if (_correctionSaving.value) return
        viewModelScope.launch {
            _correctionSaving.value = true
            try {
                repo.postCorrection(serverId, imageId, bbox)
                _correctedImageIds.update { it + imageId }
                _correctionSheetFor.value = null
                _events.emit(ResultEvent.CorrectionSaved)
            } catch (e: ApiException) {
                _events.emit(ResultEvent.CorrectionFailed(e.userMessageRes))
            } catch (_: Exception) {
                _events.emit(ResultEvent.CorrectionFailed(R.string.error_network))
            } finally {
                _correctionSaving.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, localId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { ResultViewModel(container, localId) } }
    }
}
