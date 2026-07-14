package com.audoneout.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audoneout-settings")

data class SettingsState(
    val automaticLibraryChecking: Boolean = true,
    val checkFrequency: String = "Daily",
    val wifiOnlyOnlineEnrichment: Boolean = true,
    val enhanceNewTracksAutomatically: Boolean = false,
    val analyseOnlyWhileCharging: Boolean = true,
    val notifyWhenNewTracksReady: Boolean = true,
    val quietBackgroundMode: Boolean = false,
    val onlineEnrichmentEnabled: Boolean = false
)

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val automaticLibraryChecking = booleanPreferencesKey("automatic_library_checking")
        val checkFrequency = stringPreferencesKey("check_frequency")
        val wifiOnlyOnlineEnrichment = booleanPreferencesKey("wifi_only_online_enrichment")
        val enhanceNewTracksAutomatically = booleanPreferencesKey("enhance_new_tracks_automatically")
        val analyseOnlyWhileCharging = booleanPreferencesKey("analyse_only_while_charging")
        val notifyWhenNewTracksReady = booleanPreferencesKey("notify_when_new_tracks_ready")
        val quietBackgroundMode = booleanPreferencesKey("quiet_background_mode")
        val onlineEnrichmentEnabled = booleanPreferencesKey("online_enrichment_enabled")
    }

    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            automaticLibraryChecking = prefs[Keys.automaticLibraryChecking] ?: true,
            checkFrequency = prefs[Keys.checkFrequency] ?: "Daily",
            wifiOnlyOnlineEnrichment = prefs[Keys.wifiOnlyOnlineEnrichment] ?: true,
            enhanceNewTracksAutomatically = prefs[Keys.enhanceNewTracksAutomatically] ?: false,
            analyseOnlyWhileCharging = prefs[Keys.analyseOnlyWhileCharging] ?: true,
            notifyWhenNewTracksReady = prefs[Keys.notifyWhenNewTracksReady] ?: true,
            quietBackgroundMode = prefs[Keys.quietBackgroundMode] ?: false,
            onlineEnrichmentEnabled = prefs[Keys.onlineEnrichmentEnabled] ?: false
        )
    }

    suspend fun setAutomaticLibraryChecking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.automaticLibraryChecking] = enabled }
    }
}

