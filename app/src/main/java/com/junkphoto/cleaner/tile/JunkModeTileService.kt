package com.junkphoto.cleaner.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.junkphoto.cleaner.service.PhotoMonitorService
import com.junkphoto.cleaner.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Quick Settings tile that toggles "Junk Mode" on/off.
 * When active, the [PhotoMonitorService] foreground service is started
 * to monitor the camera directory for new photos.
 */
@RequiresApi(Build.VERSION_CODES.N)
class JunkModeTileService : TileService() {

    companion object {
        private const val TAG = "JunkModeTile"
    }

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        // Sync tile state with preference
        tileScope.launch {
            val isActive = preferenceManager.isJunkModeActive.first()
            updateTileState(isActive)
        }
    }

    override fun onClick() {
        super.onClick()

        tileScope.launch {
            val currentlyActive = preferenceManager.isJunkModeActive.first()
            val newState = !currentlyActive

            preferenceManager.setJunkModeActive(newState)

            if (newState) {
                Log.d(TAG, "Junk Mode ENABLED via tile")
                PhotoMonitorService.start(applicationContext)
            } else {
                Log.d(TAG, "Junk Mode DISABLED via tile")
                PhotoMonitorService.stop(applicationContext)
            }

            updateTileState(newState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }

    private fun updateTileState(isActive: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (isActive) "Monitoring" else "Off"
            tile.updateTile()
        }
    }
}
