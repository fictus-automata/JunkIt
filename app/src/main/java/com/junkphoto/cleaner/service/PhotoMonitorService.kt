package com.junkphoto.cleaner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.junkphoto.cleaner.MainActivity
import com.junkphoto.cleaner.R
import com.junkphoto.cleaner.db.JunkPhotoDatabase
import com.junkphoto.cleaner.db.JunkPhotoEntity
import com.junkphoto.cleaner.observer.CameraDirectoryObserver
import com.junkphoto.cleaner.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Foreground service that runs while "Junk Mode" is active.
 * It uses [CameraDirectoryObserver] to monitor the camera directory
 * and automatically tags new photos as junk in the Room database.
 */
class PhotoMonitorService : Service() {

    companion object {
        private const val TAG = "PhotoMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "junk_mode_channel"

        fun start(context: Context) {
            val intent = Intent(context, PhotoMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhotoMonitorService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cameraObserver: CameraDirectoryObserver? = null
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var database: JunkPhotoDatabase

    // Inactivity timer properties
    private var inactivityTimerJob: Job? = null
    private val INACTIVITY_TIMEOUT_MS = 5L * 60L * 1000L // 5 minutes

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(applicationContext)
        database = JunkPhotoDatabase.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Junk Mode is active"))
        startMonitoring()
        resetInactivityTimer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        inactivityTimerJob?.cancel()
        cameraObserver?.stopWatching()
        cameraObserver = null
        serviceScope.cancel()
        Log.d(TAG, "PhotoMonitorService destroyed")
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val monitoredDir = preferenceManager.monitoredDirectory.first()
            val ttl = preferenceManager.ttlMillis.first()

            cameraObserver = CameraDirectoryObserver(monitoredDir) { filePath ->
                onNewPhotoDetected(filePath, ttl)
            }

            val started = cameraObserver?.startWatching() ?: false
            if (started) {
                Log.d(TAG, "Monitoring started for: $monitoredDir (TTL: ${ttl}ms)")
                updateNotification("Watching: $monitoredDir")
            } else {
                Log.e(TAG, "Failed to start monitoring: $monitoredDir")
                updateNotification("⚠ Directory not found: $monitoredDir")
            }
        }
    }

    private fun onNewPhotoDetected(filePath: String, ttlMillis: Long) {
        // Reset timer when photo is captured
        resetInactivityTimer()

        serviceScope.launch {
            try {
                // Avoid duplicates
                val existing = database.junkPhotoDao().findByPath(filePath)
                if (existing != null) {
                    Log.d(TAG, "Photo already tracked: $filePath")
                    return@launch
                }

                val file = File(filePath)
                val entity = JunkPhotoEntity(
                    filePath = filePath,
                    fileName = file.name,
                    ttlMillis = ttlMillis,
                    fileSize = file.length()
                )

                database.junkPhotoDao().insert(entity)
                Log.d(TAG, "Tagged as junk: ${file.name} (expires in ${ttlMillis / 60000} min)")

                updateNotification("Last tagged: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error tagging photo: $filePath", e)
            }
        }
    }

    private fun resetInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = serviceScope.launch {
            Log.d(TAG, "Inactivity timer started/reset. Waiting 5 minutes.")
            delay(INACTIVITY_TIMEOUT_MS)
            
            // If delay completes without cancellation, 5 minutes have passed
            Log.d(TAG, "Inactivity timeout reached. Disabling Junk Mode.")
            
            // Update preference which will update QS Tile state
            preferenceManager.setJunkModeActive(false)
            
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Junk Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Junk Mode is active and monitoring for new photos"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JunkIt")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile_junk)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
