package com.junkphoto.cleaner

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.junkphoto.cleaner.db.JunkPhotoDatabase
import com.junkphoto.cleaner.db.JunkPhotoEntity
import com.junkphoto.cleaner.service.PhotoMonitorService
import com.junkphoto.cleaner.ui.screens.HomeScreen
import com.junkphoto.cleaner.ui.screens.SettingsScreen
import com.junkphoto.cleaner.ui.theme.JunkItTheme
import com.junkphoto.cleaner.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var database: JunkPhotoDatabase

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Permission result handled in onResume */ }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferenceManager = PreferenceManager(applicationContext)
        database = JunkPhotoDatabase.getInstance(applicationContext)

        requestPermissions()

        setContent {
            JunkItTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    // Collect state
                    val isJunkModeActive by preferenceManager.isJunkModeActive.collectAsState(initial = false)
                    val ttlMillis by preferenceManager.ttlMillis.collectAsState(initial = PreferenceManager.DEFAULT_TTL_MILLIS)
                    val monitoredDir by preferenceManager.monitoredDirectory.collectAsState(initial = PreferenceManager.DEFAULT_MONITORED_DIR)
                    val activeCount by database.junkPhotoDao().getActiveCount().collectAsState(initial = 0)
                    val deletedCount by database.junkPhotoDao().getDeletedCount().collectAsState(initial = 0)
                    val junkPhotos by database.junkPhotoDao().getAllActivePhotos().collectAsState(initial = emptyList())

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                isJunkModeActive = isJunkModeActive,
                                activeCount = activeCount,
                                deletedCount = deletedCount,
                                ttlMillis = ttlMillis,
                                monitoredDir = monitoredDir,
                                junkPhotos = junkPhotos,
                                onToggleJunkMode = { enabled ->
                                    scope.launch {
                                        preferenceManager.setJunkModeActive(enabled)
                                        if (enabled) {
                                            PhotoMonitorService.start(applicationContext)
                                        } else {
                                            PhotoMonitorService.stop(applicationContext)
                                        }
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onUnjunk = { photoId ->
                                    scope.launch {
                                        // Deleting from DB un-junks the photo so it won't be deleted later
                                        database.junkPhotoDao().deleteById(photoId)
                                    }
                                },
                                onKeepPhotos = { photoIds ->
                                    scope.launch {
                                        database.junkPhotoDao().deleteByIds(photoIds)
                                    }
                                },
                                onDeletePhotos = { photos ->
                                    scope.launch(Dispatchers.IO) {
                                        val deletedIds = mutableListOf<Long>()
                                        for (photo in photos) {
                                            if (trashOrDeletePhoto(applicationContext, photo)) {
                                                deletedIds.add(photo.id)
                                            }
                                        }
                                        if (deletedIds.isNotEmpty()) {
                                            database.junkPhotoDao().markAsDeletedByIds(deletedIds)
                                        }
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                currentTtlMillis = ttlMillis,
                                currentMonitoredDir = monitoredDir,
                                onTtlChanged = { newTtl ->
                                    scope.launch { preferenceManager.setTtlMillis(newTtl) }
                                },
                                onMonitoredDirChanged = { newDir ->
                                    scope.launch { preferenceManager.setMonitoredDirectory(newDir) }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        // Request MANAGE_EXTERNAL_STORAGE for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            }
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun trashOrDeletePhoto(context: Context, photo: JunkPhotoEntity): Boolean {
        val file = File(photo.filePath)
        if (!file.exists()) {
            return true // Already gone from storage
        }

        var isTrashed = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val resolver = context.contentResolver
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
                        if (resolver.update(itemUri, values, null, null) > 0) {
                            isTrashed = true
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to move to Recycle Bin via MediaStore", e)
                    }
                }
            }
        }

        // Fallback to permanent delete if not trashed
        if (!isTrashed) {
            if (file.delete()) {
                isTrashed = true
            }
        }
        
        return isTrashed
    }
}
