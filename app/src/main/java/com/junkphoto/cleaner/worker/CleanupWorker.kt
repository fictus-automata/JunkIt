package com.junkphoto.cleaner.worker

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.junkphoto.cleaner.db.JunkPhotoDatabase
import java.io.File

/**
 * Periodic WorkManager worker that scans the database for expired junk photos
 * and deletes them from the filesystem, then marks them as deleted in the DB.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CleanupWorker"
        const val WORK_NAME = "junk_photo_cleanup"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting cleanup job...")

        return try {
            val dao = JunkPhotoDatabase.getInstance(applicationContext).junkPhotoDao()
            val now = System.currentTimeMillis()

            // Find all expired photos
            val expiredPhotos = dao.getExpiredPhotos(now)
            Log.d(TAG, "Found ${expiredPhotos.size} expired photos")

            var deletedCount = 0
            var failedCount = 0

            for (photo in expiredPhotos) {
                try {
                    val file = File(photo.filePath)
                    if (file.exists()) {
                        var isTrashed = false

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+ supports Recycle Bin via MediaStore
                            val resolver = applicationContext.contentResolver
                            
                            // Find the media URI for this file path
                            val projection = arrayOf(MediaStore.Images.Media._ID)
                            val selection = "${MediaStore.Images.Media.DATA} = ?"
                            val selectionArgs = arrayOf(photo.filePath)
                            
                            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            
                            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                                    val mediaId = cursor.getLong(idColumn)
                                    val itemUri = ContentUris.withAppendedId(uri, mediaId)
                                    
                                    val values = ContentValues().apply {
                                        put(MediaStore.MediaColumns.IS_TRASHED, 1)
                                    }
                                    
                                    try {
                                        val updated = resolver.update(itemUri, values, null, null)
                                        if (updated > 0) {
                                            isTrashed = true
                                            Log.d(TAG, "Moved to Recycle Bin: ${photo.fileName}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to move to Recycle Bin via MediaStore", e)
                                    }
                                }
                            }
                        }

                        // Fallback to permanent delete if not trashed
                        if (!isTrashed) {
                            if (file.delete()) {
                                isTrashed = true
                                Log.d(TAG, "Permanently deleted: ${photo.fileName}")
                            } else {
                                Log.w(TAG, "Failed to delete file: ${photo.filePath}")
                            }
                        }
                        
                        if (isTrashed) {
                            dao.markAsDeleted(photo.id)
                            deletedCount++
                        } else {
                            failedCount++
                        }
                    } else {
                        // File already gone (user deleted manually), just mark it
                        dao.markAsDeleted(photo.id)
                        deletedCount++
                        Log.d(TAG, "File already gone, marked as deleted: ${photo.fileName}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "Error handling photo: ${photo.filePath}", e)
                }
            }

            // Purge old deletion records (older than 30 days)
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
            dao.purgeOldRecords(thirtyDaysAgo)

            Log.d(TAG, "Cleanup complete: $deletedCount deleted, $failedCount failed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup job failed", e)
            Result.retry()
        }
    }
}
