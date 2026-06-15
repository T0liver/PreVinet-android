package hu.toliver.previnet.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.toliver.previnet.PreViNetApp
import hu.toliver.previnet.data.NotifChoice
import hu.toliver.previnet.data.db.ServerStatus
import hu.toliver.previnet.data.db.SubmissionState
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PreViNetApp).container
        val localId = inputData.getLong(KEY_LOCAL_ID, -1L)
        if (localId < 0) return Result.success()

        if (container.prefs.notifChoice.first() == NotifChoice.REFUSED) return Result.success()

        val row = container.submissions.get(localId) ?: return Result.success()
        val submission = row.submission
        val resultUrl = submission.resultUrl ?: return Result.success()
        // Only remind for uploaded submissions that haven't already been completed & reviewed.
        if (submission.state != SubmissionState.SUBMITTED) return Result.success()
        if (submission.serverStatus == ServerStatus.DONE) return Result.success()

        NotificationChannels.postResultsReminder(applicationContext, localId, resultUrl)
        return Result.success()
    }

    companion object {
        const val KEY_LOCAL_ID = "localId"
    }
}
