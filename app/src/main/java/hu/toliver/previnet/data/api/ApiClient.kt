package hu.toliver.previnet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class ApiClient(private val baseUrl: String) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** Prefixes relative backend URLs (e.g. /api/v1/masks/{id}) with the API base. */
    fun absoluteUrl(url: String): String = when {
        url.startsWith("https://") -> url
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        else -> baseUrl + url
    }

    suspend fun getDiseases(): List<String> = wrapNetwork {
        val response = client.get("$baseUrl/api/v1/diseases")
        response.requireSuccess()
        response.body()
    }

    /** Returns null when the submission is unknown to the server (404). */
    suspend fun getSubmission(id: String): SubmissionStatusDto? = wrapNetwork {
        val response = client.get("$baseUrl/api/v1/submissions/$id")
        if (response.status == HttpStatusCode.NotFound) return@wrapNetwork null
        response.requireSuccess()
        response.body()
    }

    suspend fun getResult(id: String): SubmissionResultDto = wrapNetwork {
        val response = client.get("$baseUrl/api/v1/submissions/$id/result")
        response.requireSuccess()
        response.body()
    }

    suspend fun postCorrection(id: String, imageId: String, bbox: Bbox) {
        wrapNetwork {
            val response = client.post("$baseUrl/api/v1/submissions/$id/correction") {
                contentType(ContentType.Application.Json)
                setBody(CorrectionRequestDto(imageId = imageId, correctedBbox = bbox))
            }
            response.requireSuccess()
        }
    }

    /**
     * Multipart submission. [bboxes] must be non-null only for tier 3 and contain one
     * entry per file in upload order (null for photos without a box).
     */
    suspend fun submit(
        files: List<File>,
        annotationTier: Int,
        diseaseLabel: String?,
        bboxes: List<Bbox?>?,
        gpsLat: Double?,
        gpsLon: Double?,
        notes: String?,
        deviceFingerprint: String,
        onProgress: (Float) -> Unit,
    ): SubmissionAcceptedDto = wrapNetwork {
        val form = formData {
            files.forEach { file ->
                append(
                    "images[]",
                    ChannelProvider(file.length()) { file.inputStream().toByteReadChannel(Dispatchers.IO) },
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    },
                )
            }
            append("annotation_tier", annotationTier.toString())
            if (diseaseLabel != null) append("disease_label", diseaseLabel)
            if (annotationTier == 3 && bboxes != null) {
                bboxes.forEach { bbox ->
                    append("bboxes[]", bbox?.let { json.encodeToString(Bbox.serializer(), it) } ?: "null")
                }
            }
            if (gpsLat != null && gpsLon != null) {
                append("gps_lat", gpsLat.toString())
                append("gps_lon", gpsLon.toString())
            }
            if (!notes.isNullOrBlank()) append("notes", notes)
            append("device_fingerprint", deviceFingerprint)
        }

        val response = client.post("$baseUrl/api/v1/submissions") {
            setBody(MultiPartFormDataContent(form))
            onUpload { bytesSentTotal, contentLength ->
                if (contentLength != null && contentLength > 0) {
                    onProgress((bytesSentTotal.toFloat() / contentLength).coerceIn(0f, 1f))
                }
            }
        }
        if (response.status != HttpStatusCode.Accepted) {
            throw ApiException.forStatus(response.status.value)
        }
        response.body()
    }

    private fun HttpResponse.requireSuccess() {
        if (status.value !in 200..299) throw ApiException.forStatus(status.value)
    }

    private inline fun <T> wrapNetwork(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        throw ApiException.network()
    } catch (e: Exception) {
        // Connection resets, TLS failures, serialization of half-received bodies, …
        throw ApiException.network()
    }
}
