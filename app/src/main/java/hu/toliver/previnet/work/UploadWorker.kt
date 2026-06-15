package hu.toliver.previnet.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import hu.toliver.previnet.PreViNetApp
import hu.toliver.previnet.R
import hu.toliver.previnet.data.api.ApiException
import hu.toliver.previnet.data.api.Bbox
import hu.toliver.previnet.data.db.SubmissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Uploads one queued submission. Runs with a CONNECTED constraint and exponential
 * backoff, so offline submissions go out automatically when coverage returns.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PreViNetApp).container
        val dao = container.submissions.dao
        val localId = inputData.getLong(KEY_LOCAL_ID, -1L)
        if (localId < 0) return Result.success()

        val row = dao.getByLocalId(localId) ?: return Result.success()
        if (row.submission.state == SubmissionState.SUBMITTED) return Result.success()

        val photos = row.sortedPhotos
        val files = photos.map { File(it.filePath) }
        if (files.isEmpty() || files.any { !it.exists() }) {
            dao.updateStateWithError(
                localId,
                SubmissionState.FAILED,
                applicationContext.getString(R.string.error_400),
            )
            return Result.failure()
        }

        dao.updateState(localId, SubmissionState.UPLOADING)

        val submission = row.submission
        val tier = submission.annotationTier
        val bboxes: List<Bbox?>? = if (tier == 3) {
            photos.map { photo ->
                if (photo.bboxX != null && photo.bboxY != null && photo.bboxW != null && photo.bboxH != null) {
                    Bbox(photo.bboxX, photo.bboxY, photo.bboxW, photo.bboxH)
                } else {
                    null
                }
            }
        } else {
            null
        }

        return try {
            val fingerprint = container.prefs.getOrCreateFingerprint()
            val accepted = container.api.submit(
                files = files,
                annotationTier = tier,
                diseaseLabel = submission.diseaseLabel,
                bboxes = bboxes,
                gpsLat = if (submission.shareGps) submission.gpsLat else null,
                gpsLon = if (submission.shareGps) submission.gpsLon else null,
                notes = submission.notes,
                deviceFingerprint = fingerprint,
                onProgress = { pct ->
                    setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                },
            )

            val resultUrl = container.api.absoluteUrl(accepted.resultUrl)
            dao.markSubmitted(localId, accepted.submissionId, resultUrl, System.currentTimeMillis())
            ReminderScheduler.schedule(applicationContext, localId)
            if (!container.submissions.appInForeground) {
                NotificationChannels.postUploadComplete(applicationContext, localId, resultUrl)
            }
            Result.success()
        } catch (e: ApiException) {
            if (e.isTransient) {
                // Revert so the UI shows "Waiting to upload" while WorkManager backs off.
                dao.updateState(localId, SubmissionState.QUEUED)
                Result.retry()
            } else {
                dao.updateStateWithError(
                    localId,
                    SubmissionState.FAILED,
                    applicationContext.getString(e.userMessageRes),
                )
                Result.failure()
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                dao.updateState(localId, SubmissionState.QUEUED)
            }
            throw e
        } catch (e: Exception) {
            dao.updateState(localId, SubmissionState.QUEUED)
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val localId = inputData.getLong(KEY_LOCAL_ID, 0L)
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.UPLOADS)
            .setSmallIcon(R.drawable.ic_splash_leaf)
            .setContentTitle(applicationContext.getString(R.string.notif_uploading))
            .setOngoing(true)
            .build()
        val id = (NotificationChannels.FOREGROUND_ID_BASE + localId).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_LOCAL_ID = "localId"
        const val KEY_PROGRESS = "pct"
    }
}
