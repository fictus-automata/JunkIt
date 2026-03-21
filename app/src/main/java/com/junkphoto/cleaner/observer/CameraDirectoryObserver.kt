package com.junkphoto.cleaner.observer

import android.os.Environment
import android.os.FileObserver
import android.util.Log
import java.io.File

/**
 * Monitors a camera output directory for newly created image files.
 * When a new image is detected, the [onNewPhoto] callback is invoked with the full file path.
 */
class CameraDirectoryObserver(
    private val relativeDirPath: String,
    private val onNewPhoto: (String) -> Unit
) {
    companion object {
        private const val TAG = "CameraDirObserver"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "dng")
    }

    private var fileObserver: FileObserver? = null
    private val watchedDir: File
        get() = File(Environment.getExternalStorageDirectory(), relativeDirPath)

    fun startWatching(): Boolean {
        val dir = watchedDir
        if (!dir.exists() || !dir.isDirectory) {
            Log.w(TAG, "Directory does not exist: ${dir.absolutePath}")
            return false
        }

        Log.d(TAG, "Starting to watch: ${dir.absolutePath}")

        // Watch for CLOSE_WRITE — fired when a file finishes being written (camera save complete)
        fileObserver = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                // Ignore hidden files and pending files (e.g., .pending-1773213-_PXL_b3e62838)
                if (path.startsWith(".")) return

                val extension = path.substringAfterLast('.', "").lowercase()
                if (extension !in IMAGE_EXTENSIONS) return

                val fullPath = File(dir, path).absolutePath
                Log.d(TAG, "New photo detected: $fullPath")
                onNewPhoto(fullPath)
            }
        }

        fileObserver?.startWatching()
        return true
    }

    fun stopWatching() {
        Log.d(TAG, "Stopping directory observer")
        fileObserver?.stopWatching()
        fileObserver = null
    }

    fun isWatching(): Boolean = fileObserver != null

    fun getWatchedPath(): String = watchedDir.absolutePath
}
