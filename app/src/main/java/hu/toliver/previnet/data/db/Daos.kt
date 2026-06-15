package hu.toliver.previnet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionDao {

    @Insert
    suspend fun insert(submission: SubmissionEntity): Long

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Transaction
    suspend fun insertWithPhotos(
        submission: SubmissionEntity,
        photos: List<PhotoEntity>,
    ): Long {
        val id = insert(submission)
        insertPhotos(photos.map { it.copy(submissionLocalId = id) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM submissions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubmissionWithPhotos>>

    @Transaction
    @Query("SELECT * FROM submissions WHERE localId = :localId")
    fun observeByLocalId(localId: Long): Flow<SubmissionWithPhotos?>

    @Transaction
    @Query("SELECT * FROM submissions WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): SubmissionWithPhotos?

    @Transaction
    @Query("SELECT * FROM submissions WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): SubmissionWithPhotos?

    @Query("SELECT COUNT(*) FROM submissions WHERE state IN ('QUEUED', 'UPLOADING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM submissions WHERE state = 'SUBMITTED' AND (serverStatus IS NULL OR serverStatus != 'done')")
    suspend fun getSubmittedNotDone(): List<SubmissionEntity>

    @Query("UPDATE submissions SET state = :state WHERE localId = :localId")
    suspend fun updateState(localId: Long, state: String)

    @Query("UPDATE submissions SET state = :state, lastError = :error WHERE localId = :localId")
    suspend fun updateStateWithError(localId: Long, state: String, error: String?)

    @Query(
        "UPDATE submissions SET serverId = :serverId, resultUrl = :resultUrl, uploadedAt = :uploadedAt, " +
            "state = 'SUBMITTED', serverStatus = 'pending', lastError = NULL WHERE localId = :localId"
    )
    suspend fun markSubmitted(localId: Long, serverId: String, resultUrl: String, uploadedAt: Long)

    @Query("UPDATE submissions SET serverStatus = :status WHERE localId = :localId")
    suspend fun updateServerStatus(localId: Long, status: String)

    @Query("UPDATE photos SET serverImageId = :serverImageId WHERE id = :photoId")
    suspend fun updatePhotoServerImageId(photoId: Long, serverImageId: String)

    @Query("DELETE FROM submissions WHERE localId = :localId")
    suspend fun delete(localId: Long)
}
