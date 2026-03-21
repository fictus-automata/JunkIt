package com.junkphoto.cleaner

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.junkphoto.cleaner.worker.CleanupWorker
import java.util.concurrent.TimeUnit

class JunkItApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleCleanupWork(this)
    }

    companion object {
        /**
         * Schedules a periodic cleanup worker that runs every 6 hours
         * to delete expired junk photos.
         */
        fun scheduleCleanupWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .addTag(CleanupWorker.TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
        }
    }
}
