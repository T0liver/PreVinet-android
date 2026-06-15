package hu.toliver.previnet.data.api

import androidx.annotation.StringRes
import hu.toliver.previnet.R

/**
 * A failed API call mapped to a user-facing message. Raw HTTP codes and exception
 * text are never shown to the user — [userMessageRes] is the single mapping point.
 */
class ApiException(
    val code: Int,
    @StringRes val userMessageRes: Int,
) : Exception("API error $code") {

    /** Transient failures are retried by WorkManager; permanent ones surface to the user. */
    val isTransient: Boolean
        get() = code == 0 || code == 429 || code >= 500

    companion object {
        const val CODE_NETWORK = 0

        fun forStatus(code: Int): ApiException = ApiException(code, messageFor(code))

        fun network(): ApiException = ApiException(CODE_NETWORK, R.string.error_network)

        @StringRes
        private fun messageFor(code: Int): Int = when (code) {
            400 -> R.string.error_400
            403 -> R.string.error_403
            413 -> R.string.error_413
            422 -> R.string.error_422
            429 -> R.string.error_429
            in 500..599 -> R.string.error_5xx
            else -> R.string.error_400
        }
    }
}
