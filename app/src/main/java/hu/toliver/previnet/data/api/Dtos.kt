package hu.toliver.previnet.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Normalized bounding box, all values 0–1 relative to image dimensions. */
@Serializable
data class Bbox(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

@Serializable
data class SubmissionAcceptedDto(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("status") val status: String,
    @SerialName("image_count") val imageCount: Int,
    @SerialName("result_url") val resultUrl: String,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class SubmissionStatusDto(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("status") val status: String,
    @SerialName("image_count") val imageCount: Int,
    @SerialName("annotation_tier") val annotationTier: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ResultImageDto(
    @SerialName("image_id") val imageId: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("mask_url") val maskUrl: String? = null,
    @SerialName("disease_label") val diseaseLabel: String? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("quality_score") val qualityScore: Double? = null,
    @SerialName("corrected") val corrected: Boolean = false,
)

@Serializable
data class SubmissionResultDto(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("status") val status: String,
    @SerialName("images") val images: List<ResultImageDto> = emptyList(),
)

@Serializable
data class CorrectionRequestDto(
    @SerialName("image_id") val imageId: String,
    @SerialName("corrected_bbox") val correctedBbox: Bbox,
)

@Serializable
data class ApiErrorDto(
    @SerialName("error") val error: String? = null,
)
