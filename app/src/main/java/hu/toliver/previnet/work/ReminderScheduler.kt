package hu.toliver.previnet.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    /**
     * Schedules the "results may be ready" reminder for 18:00 local time today,
     * or 10:00 tomorrow morning when it is already past 17:00.
     */
    fun schedule(context: Context, localId: Long) {
        val now = LocalDateTime.now()
        val target = if (now.hour >= 17) {
            now.plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.withHour(18).withMinute(0).withSecond(0).withNano(0)
        }
        val delayMs = Duration.between(now, target).toMillis().coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_LOCAL_ID to localId))
            .addTag(workName(localId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(localId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancelled once the user has actually seen the segmented result. */
    fun cancel(context: Context, localId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(localId))
    }

    private fun workName(localId: Long) = "reminder-$localId"
}
