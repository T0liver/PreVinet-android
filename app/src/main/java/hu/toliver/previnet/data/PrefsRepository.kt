package hu.toliver.previnet.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "previNet_prefs")

enum class NotifChoice { UNASKED, ACCEPTED, REFUSED }

class PrefsRepository(private val context: Context) {

    private object Keys {
        val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        val FINGERPRINT = stringPreferencesKey("device_fingerprint")
        val DISEASES_JSON = stringPreferencesKey("diseases_json")
        val NOTIF_CHOICE = stringPreferencesKey("notif_choice")
        val BBOX_TUTORIAL_SHOWN = booleanPreferencesKey("bbox_tutorial_shown")
    }

    val consentGiven: Flow<Boolean> = context.dataStore.data.map { it[Keys.CONSENT_GIVEN] ?: false }

    suspend fun setConsentGiven(value: Boolean) {
        context.dataStore.edit { it[Keys.CONSENT_GIVEN] = value }
    }

    val diseasesJson: Flow<String?> = context.dataStore.data.map { it[Keys.DISEASES_JSON] }

    suspend fun setDiseasesJson(json: String) {
        context.dataStore.edit { it[Keys.DISEASES_JSON] = json }
    }

    val notifChoice: Flow<NotifChoice> = context.dataStore.data.map {
        when (it[Keys.NOTIF_CHOICE]) {
            "accepted" -> NotifChoice.ACCEPTED
            "refused" -> NotifChoice.REFUSED
            else -> NotifChoice.UNASKED
        }
    }

    suspend fun setNotifChoice(choice: NotifChoice) {
        context.dataStore.edit {
            it[Keys.NOTIF_CHOICE] = when (choice) {
                NotifChoice.ACCEPTED -> "accepted"
                NotifChoice.REFUSED -> "refused"
                NotifChoice.UNASKED -> "unasked"
            }
        }
    }

    val bboxTutorialShown: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BBOX_TUTORIAL_SHOWN] ?: false }

    suspend fun setBboxTutorialShown() {
        context.dataStore.edit { it[Keys.BBOX_TUTORIAL_SHOWN] = true }
    }

    /** Computed once, then cached for the life of the install. */
    suspend fun getOrCreateFingerprint(): String {
        val cached = context.dataStore.data.first()[Keys.FINGERPRINT]
        if (cached != null) return cached
        val fingerprint = Fingerprint.compute(context)
        context.dataStore.edit { it[Keys.FINGERPRINT] = fingerprint }
        return fingerprint
    }
}
