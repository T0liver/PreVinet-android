package hu.toliver.previnet.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hu.toliver.previnet.MainActivity
import hu.toliver.previnet.R

object NotificationChannels {

    const val UPLOADS = "uploads"
    const val RESULTS = "results"

    fun create(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                UPLOADS,
                context.getString(R.string.channel_uploads),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RESULTS,
                context.getString(R.string.channel_results),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    fun resultPendingIntent(context: Context, resultUrl: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(resultUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            resultUrl.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "Photos uploaded ✓" — posted when an upload completes while the app is backgrounded. */
    fun postUploadComplete(context: Context, localId: Long, resultUrl: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, UPLOADS)
            .setSmallIcon(R.drawable.ic_splash_leaf)
            .setContentTitle(context.getString(R.string.notif_uploaded_title))
            .setContentText(context.getString(R.string.notif_uploaded_text))
            .setContentIntent(resultPendingIntent(context, resultUrl))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((UPLOAD_DONE_ID_BASE + localId).toInt(), notification)
        }
    }

    fun postResultsReminder(context: Context, localId: Long, resultUrl: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, RESULTS)
            .setSmallIcon(R.drawable.ic_splash_leaf)
            .setContentTitle(context.getString(R.string.notif_results_title))
            .setContentText(context.getString(R.string.notif_results_text))
            .setContentIntent(resultPendingIntent(context, resultUrl))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((REMINDER_ID_BASE + localId).toInt(), notification)
        }
    }

    private const val UPLOAD_DONE_ID_BASE = 1_000L
    private const val REMINDER_ID_BASE = 2_000L
    const val FOREGROUND_ID_BASE = 3_000L
}
