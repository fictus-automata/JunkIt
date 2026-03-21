package com.junkphoto.cleaner.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "junk_photos")
data class JunkPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val ttlMillis: Long,
    val expiresAt: Long = capturedAt + ttlMillis,
    val fileSize: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long? = null
)
