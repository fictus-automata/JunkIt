package com.junkphoto.cleaner.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JunkPhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: JunkPhotoEntity): Long

    @Query("SELECT * FROM junk_photos WHERE deleted = 0 ORDER BY capturedAt DESC")
    fun getAllActivePhotos(): Flow<List<JunkPhotoEntity>>

    @Query("SELECT * FROM junk_photos WHERE deleted = 0 AND expiresAt <= :now")
    suspend fun getExpiredPhotos(now: Long = System.currentTimeMillis()): List<JunkPhotoEntity>

    @Query("SELECT COUNT(*) FROM junk_photos WHERE deleted = 0")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM junk_photos WHERE deleted = 1")
    fun getDeletedCount(): Flow<Int>

    @Query("UPDATE junk_photos SET deleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun markAsDeleted(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM junk_photos WHERE deleted = 1 AND deletedAt < :before")
    suspend fun purgeOldRecords(before: Long)

    @Query("DELETE FROM junk_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM junk_photos WHERE filePath = :path AND deleted = 0 LIMIT 1")
    suspend fun findByPath(path: String): JunkPhotoEntity?
}
