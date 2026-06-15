package hu.toliver.previnet.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object Fingerprint {

    /** SHA-256 hex of stable device identifiers — the backend's anonymous device key. */
    @SuppressLint("HardwareIds")
    fun compute(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val raw = "$androidId|${Build.MANUFACTURER}|${Build.MODEL}|${Build.VERSION.SDK_INT}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
