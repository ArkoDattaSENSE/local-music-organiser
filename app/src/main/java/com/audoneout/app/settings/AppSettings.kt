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
    val enhanceNewTracksAutomatically: Boolean = true,
    val analyseOnlyWhileCharging: Boolean = true,
    val notifyWhenNewTracksReady: Boolean = true,
    val quietBackgroundMode: Boolean = false,
    val onlineEnrichmentEnabled: Boolean = false,
    val lastFmEnabled: Boolean = false,
    val lastFmUsername: String = "",
    val lastFmApiKey: String = ""
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
        val lastFmEnabled = booleanPreferencesKey("lastfm_enabled")
        val lastFmUsername = stringPreferencesKey("lastfm_username")
        val lastFmApiKey = stringPreferencesKey("lastfm_api_key")
    }

    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            automaticLibraryChecking = prefs[Keys.automaticLibraryChecking] ?: true,
            checkFrequency = prefs[Keys.checkFrequency] ?: "Daily",
            wifiOnlyOnlineEnrichment = prefs[Keys.wifiOnlyOnlineEnrichment] ?: true,
            enhanceNewTracksAutomatically = prefs[Keys.enhanceNewTracksAutomatically] ?: true,
            analyseOnlyWhileCharging = prefs[Keys.analyseOnlyWhileCharging] ?: true,
            notifyWhenNewTracksReady = prefs[Keys.notifyWhenNewTracksReady] ?: true,
            quietBackgroundMode = prefs[Keys.quietBackgroundMode] ?: false,
            onlineEnrichmentEnabled = prefs[Keys.onlineEnrichmentEnabled] ?: false,
            lastFmEnabled = prefs[Keys.lastFmEnabled] ?: false,
            lastFmUsername = prefs[Keys.lastFmUsername].orEmpty(),
            lastFmApiKey = prefs[Keys.lastFmApiKey].orEmpty()
        )
    }

    suspend fun setAutomaticLibraryChecking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.automaticLibraryChecking] = enabled }
    }

    suspend fun setCheckFrequency(frequency: String) {
        context.dataStore.edit { it[Keys.checkFrequency] = if (frequency == "Weekly") "Weekly" else "Daily" }
    }

    suspend fun setWifiOnlyOnlineEnrichment(enabled: Boolean) {
        context.dataStore.edit { it[Keys.wifiOnlyOnlineEnrichment] = enabled }
    }

    suspend fun setEnhanceNewTracksAutomatically(enabled: Boolean) {
        context.dataStore.edit { it[Keys.enhanceNewTracksAutomatically] = enabled }
    }

    suspend fun setAnalyseOnlyWhileCharging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.analyseOnlyWhileCharging] = enabled }
    }

    suspend fun setNotifyWhenNewTracksReady(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyWhenNewTracksReady] = enabled }
    }

    suspend fun setQuietBackgroundMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.quietBackgroundMode] = enabled }
    }

    suspend fun setOnlineEnrichmentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.onlineEnrichmentEnabled] = enabled }
    }

    suspend fun setLastFmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.lastFmEnabled] = enabled }
    }

    suspend fun setLastFmCredentials(username: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.lastFmUsername] = username.trim()
            prefs[Keys.lastFmApiKey] = apiKey.trim()
            prefs[Keys.lastFmEnabled] = username.isNotBlank() && apiKey.isNotBlank()
        }
    }
}
