package hu.toliver.previnet.data

import androidx.annotation.StringRes
import hu.toliver.previnet.R
import hu.toliver.previnet.data.api.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class Disease(
    val slug: String,
    /** Resource for known slugs; null for server-only slugs (use [fallbackName]). */
    @StringRes val nameRes: Int?,
    @StringRes val descriptionRes: Int?,
    val fallbackName: String,
) {
    val isUnknownOption: Boolean get() = slug == DiseaseRepository.UNKNOWN_SLUG
}

class DiseaseRepository(
    private val api: ApiClient,
    private val prefs: PrefsRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }

    val diseases: Flow<List<Disease>> = prefs.diseasesJson.map { cached ->
        val slugs = cached?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        } ?: FALLBACK_SLUGS
        toDiseases(slugs)
    }

    /** Refresh the slug list from the server when online; failures keep the cached/bundled list. */
    suspend fun refresh() {
        runCatching {
            val slugs = api.getDiseases()
            if (slugs.isNotEmpty()) {
                prefs.setDiseasesJson(json.encodeToString(ListSerializer(String.serializer()), slugs))
            }
        }
    }

    private fun toDiseases(slugs: List<String>): List<Disease> {
        val diseases = slugs.map { slug ->
            val known = KNOWN[slug]
            Disease(
                slug = slug,
                nameRes = known?.first,
                descriptionRes = known?.second,
                fallbackName = titleCase(slug),
            )
        }
        // "I'm not sure" always goes last.
        return diseases.filter { !it.isUnknownOption } + diseases.filter { it.isUnknownOption }
    }

    private fun titleCase(slug: String): String =
        slug.split('_', '-').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }

    companion object {
        const val UNKNOWN_SLUG = "unknown"

        val FALLBACK_SLUGS = listOf(
            "black_rot", "peronospora", "powdery_mildew", "botrytis", "esca", "phomopsis", "unknown",
        )

        private val KNOWN: Map<String, Pair<Int, Int?>> = mapOf(
            "black_rot" to (R.string.disease_black_rot to R.string.disease_black_rot_desc),
            "peronospora" to (R.string.disease_peronospora to R.string.disease_peronospora_desc),
            "powdery_mildew" to (R.string.disease_powdery_mildew to R.string.disease_powdery_mildew_desc),
            "botrytis" to (R.string.disease_botrytis to R.string.disease_botrytis_desc),
            "esca" to (R.string.disease_esca to R.string.disease_esca_desc),
            "phomopsis" to (R.string.disease_phomopsis to R.string.disease_phomopsis_desc),
            "unknown" to (R.string.disease_unknown to null),
        )
    }
}
