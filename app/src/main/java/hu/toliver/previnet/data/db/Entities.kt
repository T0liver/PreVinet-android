package hu.toliver.previnet.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object SubmissionState {
    const val QUEUED = "QUEUED"
    const val UPLOADING = "UPLOADING"
    const val FAILED = "FAILED"
    const val SUBMITTED = "SUBMITTED"
}

object ServerStatus {
    const val PENDING = "pending"
    const val RELAYED = "relayed"
    const val SEGMENTED = "segmented"
    const val DONE = "done"
}

@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: String? = null,
    val state: String,
    val serverStatus: String? = null,
    val annotationTier: Int,
    val diseaseLabel: String? = null,
    val notes: String? = null,
    val shareGps: Boolean = true,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val createdAt: Long,
    val uploadedAt: Long? = null,
    val resultUrl: String? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = SubmissionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["submissionLocalId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("submissionLocalId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val submissionLocalId: Long,
    val orderIndex: Int,
    val filePath: String,
    val fileSize: Long,
    val bboxX: Double? = null,
    val bboxY: Double? = null,
    val bboxW: Double? = null,
    val bboxH: Double? = null,
    val serverImageId: String? = null,
)

data class SubmissionWithPhotos(
    @Embedded val submission: SubmissionEntity,
    @Relation(parentColumn = "localId", entityColumn = "submissionLocalId")
    val photos: List<PhotoEntity>,
) {
    val sortedPhotos: List<PhotoEntity> get() = photos.sortedBy { it.orderIndex }
}
