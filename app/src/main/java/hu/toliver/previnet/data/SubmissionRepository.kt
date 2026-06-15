package hu.toliver.previnet.data

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import hu.toliver.previnet.data.api.ApiClient
import hu.toliver.previnet.data.api.Bbox
import hu.toliver.previnet.data.api.SubmissionResultDto
import hu.toliver.previnet.data.db.AppDatabase
import hu.toliver.previnet.data.db.PhotoEntity
import hu.toliver.previnet.data.db.ServerStatus
import hu.toliver.previnet.data.db.SubmissionEntity
import hu.toliver.previnet.data.db.SubmissionState
import hu.toliver.previnet.data.db.SubmissionWithPhotos
import hu.toliver.previnet.work.ReminderScheduler
import hu.toliver.previnet.work.UploadWorker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

data class DraftPhoto(
    val file: File,
    val sizeBytes: Long,
    val lat: Double?,
    val lon: Double?,
    val bbox: Bbox?,
)

data class SubmissionDraft(
    val photos: List<DraftPhoto>,
    val diseaseLabel: String?,
    val notes: String?,
    val shareGps: Boolean,
)

class SubmissionRepository(
    private val app: Application,
    db: AppDatabase,
    private val api: ApiClient,
    private val imageStore: ImageStore,
) {
    val dao = db.submissionDao()
    private val workManager = WorkManager.getInstance(app)

    /** Set from MainActivity lifecycle; the upload worker skips its "uploaded" notification when true. */
    @Volatile
    var appInForeground: Boolean = false

    fun observeAll(): Flow<List<SubmissionWithPhotos>> = dao.observeAll()

    fun observe(localId: Long): Flow<SubmissionWithPhotos?> = dao.observeByLocalId(localId)

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun get(localId: Long): SubmissionWithPhotos? = dao.getByLocalId(localId)

    /**
     * The single submission path, online or offline: persist locally, then hand the
     * upload to WorkManager (which waits for connectivity and survives process death).
     */
    suspend fun enqueue(draft: SubmissionDraft): Long {
        val tier = computeTier(draft)
        val gps = if (draft.shareGps) {
            draft.photos.firstOrNull { it.lat != null && it.lon != null }
        } else {
            null
        }
        val submission = SubmissionEntity(
            state = SubmissionState.QUEUED,
            annotationTier = tier,
            diseaseLabel = draft.diseaseLabel,
            notes = draft.notes?.take(500)?.takeIf { it.isNotBlank() },
            shareGps = draft.shareGps,
            gpsLat = gps?.lat,
            gpsLon = gps?.lon,
            createdAt = System.currentTimeMillis(),
        )
        val photos = draft.photos.mapIndexed { index, photo ->
            PhotoEntity(
                submissionLocalId = 0,
                orderIndex = index,
                filePath = photo.file.absolutePath,
                fileSize = photo.sizeBytes,
                bboxX = photo.bbox?.x,
                bboxY = photo.bbox?.y,
                bboxW = photo.bbox?.w,
                bboxH = photo.bbox?.h,
            )
        }
        val localId = dao.insertWithPhotos(submission, photos)
        enqueueUpload(localId, ExistingWorkPolicy.KEEP)
        return localId
    }

    fun computeTier(draft: SubmissionDraft): Int = when {
        draft.diseaseLabel != null && draft.photos.any { it.bbox != null } -> 3
        draft.diseaseLabel != null -> 2
        else -> 1
    }

    fun enqueueUpload(localId: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(UploadWorker.KEY_LOCAL_ID to localId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(uploadWorkName(localId), policy, request)
    }

    suspend fun retry(localId: Long) {
        dao.updateStateWithError(localId, SubmissionState.QUEUED, null)
        enqueueUpload(localId, ExistingWorkPolicy.REPLACE)
    }

    suspend fun delete(localId: Long) {
        workManager.cancelUniqueWork(uploadWorkName(localId))
        ReminderScheduler.cancel(app, localId)
        val row = dao.getByLocalId(localId) ?: return
        dao.delete(localId)
        imageStore.delete(row.photos.map { it.filePath })
    }

    /** WorkManager progress (0–1) for the live upload bar on the submit screen. */
    fun uploadProgress(localId: Long): Flow<Float> =
        workManager.getWorkInfosForUniqueWorkFlow(uploadWorkName(localId)).map { infos ->
            val info = infos.firstOrNull() ?: return@map 0f
            if (info.state == WorkInfo.State.RUNNING) {
                info.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f)
            } else {
                0f
            }
        }

    /**
     * Resolves an opened result link to a local row. Links shared from other devices
     * get a photo-less stub row; everything then loads from the server.
     */
    suspend fun resolveDeepLink(serverId: String, resultUrl: String): Long {
        dao.getByServerId(serverId)?.let { return it.submission.localId }
        return dao.insert(
            SubmissionEntity(
                serverId = serverId,
                state = SubmissionState.SUBMITTED,
                annotationTier = 1,
                createdAt = System.currentTimeMillis(),
                uploadedAt = System.currentTimeMillis(),
                resultUrl = resultUrl,
            )
        )
    }

    /** Returns the fresh server status, or null when it cannot be determined right now. */
    suspend fun refreshStatus(localId: Long): String? {
        val row = dao.getByLocalId(localId) ?: return null
        val serverId = row.submission.serverId ?: return null
        val dto = api.getSubmission(serverId) ?: return STATUS_NOT_FOUND
        dao.updateServerStatus(localId, dto.status)
        return dto.status
    }

    /** Best-effort status refresh for every uploaded-but-unfinished submission. */
    suspend fun refreshAllStatuses() = coroutineScope {
        dao.getSubmittedNotDone().forEach { submission ->
            val serverId = submission.serverId ?: return@forEach
            launch {
                runCatching {
                    api.getSubmission(serverId)?.let {
                        dao.updateServerStatus(submission.localId, it.status)
                    }
                }
            }
        }
    }

    /**
     * Fetches segmentation results and maps server image ids onto local photos by
     * order index (the result array is in upload order).
     */
    suspend fun fetchResult(localId: Long): SubmissionResultDto? {
        val row = dao.getByLocalId(localId) ?: return null
        val serverId = row.submission.serverId ?: return null
        val result = api.getResult(serverId)
        dao.updateServerStatus(localId, result.status)
        val photosByIndex = row.sortedPhotos
        result.images.forEachIndexed { index, image ->
            photosByIndex.getOrNull(index)?.let { photo ->
                if (photo.serverImageId != image.imageId) {
                    dao.updatePhotoServerImageId(photo.id, image.imageId)
                }
            }
        }
        return result
    }

    suspend fun postCorrection(serverId: String, imageId: String, bbox: Bbox) {
        api.postCorrection(serverId, imageId, bbox)
    }

    /** Called when the user has actually seen a segmented result. */
    fun cancelReminder(localId: Long) {
        ReminderScheduler.cancel(app, localId)
    }

    fun isDone(status: String?): Boolean =
        status == ServerStatus.SEGMENTED || status == ServerStatus.DONE

    companion object {
        const val STATUS_NOT_FOUND = "not_found"
        fun uploadWorkName(localId: Long) = "upload-$localId"
    }
}
