package com.junkphoto.cleaner.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.junkphoto.cleaner.JunkItApp

/**
 * Reschedules the periodic cleanup work after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — rescheduling cleanup worker")
            JunkItApp.scheduleCleanupWork(context)
        }
    }
}
