package com.junkphoto.cleaner.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "junk_cleaner_prefs")

class PreferenceManager(private val context: Context) {

    companion object {
        private val KEY_JUNK_MODE_ACTIVE = booleanPreferencesKey("junk_mode_active")
        private val KEY_TTL_MILLIS = longPreferencesKey("ttl_millis")
        private val KEY_MONITORED_DIR = stringPreferencesKey("monitored_directory")

        // Default TTL: 24 hours
        const val DEFAULT_TTL_MILLIS = 24 * 60 * 60 * 1000L

        // Default monitored directory
        const val DEFAULT_MONITORED_DIR = "DCIM/Camera"

        // Preset TTL options
        val TTL_OPTIONS = mapOf(
            "1 Hour" to 1 * 60 * 60 * 1000L,
            "6 Hours" to 6 * 60 * 60 * 1000L,
            "24 Hours" to 24 * 60 * 60 * 1000L,
            "3 Days" to 3 * 24 * 60 * 60 * 1000L,
            "7 Days" to 7 * 24 * 60 * 60 * 1000L,
            "30 Days" to 30 * 24 * 60 * 60 * 1000L
        )
    }

    val isJunkModeActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_JUNK_MODE_ACTIVE] ?: false
    }

    val ttlMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTL_MILLIS] ?: DEFAULT_TTL_MILLIS
    }

    val monitoredDirectory: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MONITORED_DIR] ?: DEFAULT_MONITORED_DIR
    }

    suspend fun setJunkModeActive(active: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_JUNK_MODE_ACTIVE] = active
        }
    }

    suspend fun setTtlMillis(ttl: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TTL_MILLIS] = ttl
        }
    }

    suspend fun setMonitoredDirectory(dir: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONITORED_DIR] = dir
        }
    }
}
