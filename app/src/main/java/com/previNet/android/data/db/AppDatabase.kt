package com.previNet.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SubmissionEntity::class, PhotoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
}
