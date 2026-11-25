package com.notremock.app.data.prefs

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notremock.app.data.model.City
import com.notremock.app.data.model.MccTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notremock_prefs")

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    PERSIAN("fa");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class PanicAction { STOP_MOCKING, JUMP_FALLBACK_CITY }
enum class TimeZoneMode { MANUAL, SHIZUKU, ROOT }

data class AppSettings(
    val selectedCityId: Long = 2950159L, // Berlin default
    val favoriteCityIds: List<Long> = listOf(2950159L, 2759794L, 2643743L, 5128581L, 112931L),
    val recentCityIds: List<Long> = emptyList(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoStartOnBoot: Boolean = false,
    val wasMockingActive: Boolean = false,
    val bypassEnabled: Boolean = true,
    val bypassPackages: Set<String> = DEFAULT_BYPASS_PACKAGES,
    val jitterMeters: Int = 3,
    val cityRadiusKm: Float = 1.2f,
    val updateIntervalMs: Int = 1000,
    val altitudeMeters: Int = 45,
    val watchdogEnabled: Boolean = true,
    val stealthNotification: Boolean = false,
    val panicAction: PanicAction = PanicAction.STOP_MOCKING,
    val fallbackCityId: Long = 2950159L,
    val biometricLock: Boolean = false,
    val syncTimeZone: Boolean = false,
    val timeZoneMode: TimeZoneMode = TimeZoneMode.MANUAL,
    val allowIpCheck: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    // Root / LSPosed Pro options
    val xposedHideSim: Boolean = false,
    val xposedHideIsMock: Boolean = false,
    val xposedPerAppTz: Boolean = false,
    val xposedPerAppBypass: Boolean = true
) {
    companion object {
        val DEFAULT_BYPASS_PACKAGES = setOf(
            "org.rajman.neshan.traffic.navigator", // Neshan
            "ir.balad",                             // Balad
            "com.google.android.apps.maps",         // Google Maps
            "com.waze",                             // Waze
            "cab.snapp.passenger",                  // Snapp
            "ir.tapsi.passenger"                    // Tapsi
        )
    }
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_CITY_ID = longPreferencesKey("selected_city_id")
        val FAVORITE_CITY_IDS = stringPreferencesKey("favorite_city_ids")
        val RECENT_CITY_IDS = stringPreferencesKey("recent_city_ids")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val WAS_MOCKING_ACTIVE = booleanPreferencesKey("was_mocking_active")
        val BYPASS_ENABLED = booleanPreferencesKey("bypass_enabled")
        val BYPASS_PACKAGES = stringSetPreferencesKey("bypass_packages")
        val JITTER_METERS = intPreferencesKey("jitter_meters")
        val CITY_RADIUS_KM = floatPreferencesKey("city_radius_km")
        val UPDATE_INTERVAL_MS = intPreferencesKey("update_interval_ms")
        val ALTITUDE_METERS = intPreferencesKey("altitude_meters")
        val WATCHDOG_ENABLED = booleanPreferencesKey("watchdog_enabled")
        val STEALTH_NOTIFICATION = booleanPreferencesKey("stealth_notification")
        val PANIC_ACTION = stringPreferencesKey("panic_action")
        val FALLBACK_CITY_ID = longPreferencesKey("fallback_city_id")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SYNC_TIMEZONE = booleanPreferencesKey("sync_timezone")
        val TIMEZONE_MODE = stringPreferencesKey("timezone_mode")
        val ALLOW_IP_CHECK = booleanPreferencesKey("allow_ip_check")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val XPOSED_HIDE_SIM = booleanPreferencesKey("xposed_hide_sim")
        val XPOSED_HIDE_ISMOCK = booleanPreferencesKey("xposed_hide_ismock")
        val XPOSED_PER_APP_TZ = booleanPreferencesKey("xposed_per_app_tz")
        val XPOSED_PER_APP_BYPASS = booleanPreferencesKey("xposed_per_app_bypass")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            selectedCityId = prefs[Keys.SELECTED_CITY_ID] ?: 2950159L,
            favoriteCityIds = decodeIds(prefs[Keys.FAVORITE_CITY_IDS])
                ?: listOf(2950159L, 2759794L, 2643743L, 5128581L, 112931L),
            recentCityIds = decodeIds(prefs[Keys.RECENT_CITY_IDS]) ?: emptyList(),
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE] ?: ""),
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "DARK") }
                .getOrDefault(ThemeMode.DARK),
            autoStartOnBoot = prefs[Keys.AUTO_START_BOOT] ?: false,
            wasMockingActive = prefs[Keys.WAS_MOCKING_ACTIVE] ?: false,
            bypassEnabled = prefs[Keys.BYPASS_ENABLED] ?: true,
            bypassPackages = prefs[Keys.BYPASS_PACKAGES] ?: AppSettings.DEFAULT_BYPASS_PACKAGES,
            jitterMeters = prefs[Keys.JITTER_METERS] ?: 3,
            cityRadiusKm = prefs[Keys.CITY_RADIUS_KM] ?: 1.2f,
            updateIntervalMs = prefs[Keys.UPDATE_INTERVAL_MS] ?: 1000,
            altitudeMeters = prefs[Keys.ALTITUDE_METERS] ?: 45,
            watchdogEnabled = prefs[Keys.WATCHDOG_ENABLED] ?: true,
            stealthNotification = prefs[Keys.STEALTH_NOTIFICATION] ?: false,
            panicAction = runCatching { PanicAction.valueOf(prefs[Keys.PANIC_ACTION] ?: "STOP_MOCKING") }
                .getOrDefault(PanicAction.STOP_MOCKING),
            fallbackCityId = prefs[Keys.FALLBACK_CITY_ID] ?: 2950159L,
            biometricLock = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            syncTimeZone = prefs[Keys.SYNC_TIMEZONE] ?: false,
            timeZoneMode = runCatching { TimeZoneMode.valueOf(prefs[Keys.TIMEZONE_MODE] ?: "MANUAL") }
                .getOrDefault(TimeZoneMode.MANUAL),
            allowIpCheck = prefs[Keys.ALLOW_IP_CHECK] ?: false,
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            xposedHideSim = prefs[Keys.XPOSED_HIDE_SIM] ?: false,
            xposedHideIsMock = prefs[Keys.XPOSED_HIDE_ISMOCK] ?: false,
            xposedPerAppTz = prefs[Keys.XPOSED_PER_APP_TZ] ?: false,
            xposedPerAppBypass = prefs[Keys.XPOSED_PER_APP_BYPASS] ?: true
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun selectCity(city: City) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CITY_ID] = city.id
            val recent = (listOf(city.id) + (decodeIds(prefs[Keys.RECENT_CITY_IDS]) ?: emptyList()))
                .distinct()
                .take(8)
            prefs[Keys.RECENT_CITY_IDS] = recent.joinToString(",")
        }
        syncXposedBridge(city = city, active = null)
    }

    suspend fun toggleFavorite(cityId: Long) {
        context.dataStore.edit { prefs ->
            val current = (decodeIds(prefs[Keys.FAVORITE_CITY_IDS])
                ?: listOf(2950159L, 2759794L, 2643743L, 5128581L, 112931L)).toMutableList()
            if (current.contains(cityId)) current.remove(cityId) else current.add(0, cityId)
            prefs[Keys.FAVORITE_CITY_IDS] = current.take(15).joinToString(",")
        }
    }

    suspend fun setMockingActiveState(active: Boolean, activeCity: City? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAS_MOCKING_ACTIVE] = active
        }
        syncXposedBridge(city = activeCity, active = active)
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun updateTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_BOOT] = enabled }
    }

    suspend fun updateBypassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BYPASS_ENABLED] = enabled }
        syncXposedBridge()
    }

    suspend fun toggleBypassPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.BYPASS_PACKAGES] ?: AppSettings.DEFAULT_BYPASS_PACKAGES).toMutableSet()
            if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
            prefs[Keys.BYPASS_PACKAGES] = current
        }
        syncXposedBridge()
    }

    suspend fun updateRealism(jitterMeters: Int, cityRadiusKm: Float, intervalMs: Int, altitudeMeters: Int) {
        context.dataStore.edit {
            it[Keys.JITTER_METERS] = jitterMeters
            it[Keys.CITY_RADIUS_KM] = cityRadiusKm
            it[Keys.UPDATE_INTERVAL_MS] = intervalMs
            it[Keys.ALTITUDE_METERS] = altitudeMeters
        }
    }

    suspend fun updateKillSwitch(watchdog: Boolean, stealth: Boolean, panicAction: PanicAction, fallbackCityId: Long) {
        context.dataStore.edit {
            it[Keys.WATCHDOG_ENABLED] = watchdog
            it[Keys.STEALTH_NOTIFICATION] = stealth
            it[Keys.PANIC_ACTION] = panicAction.name
            it[Keys.FALLBACK_CITY_ID] = fallbackCityId
        }
    }

    suspend fun updateBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun updateSyncTimeZone(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SYNC_TIMEZONE] = enabled }
        syncXposedBridge()
    }

    suspend fun updateTimeZoneMode(mode: TimeZoneMode) {
        context.dataStore.edit { it[Keys.TIMEZONE_MODE] = mode.name }
    }

    suspend fun updateAllowIpCheck(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_IP_CHECK] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun updateXposedSettings(
        hideSim: Boolean? = null,
        hideIsMock: Boolean? = null,
        perAppTz: Boolean? = null,
        perAppBypass: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            hideSim?.let { prefs[Keys.XPOSED_HIDE_SIM] = it }
            hideIsMock?.let { prefs[Keys.XPOSED_HIDE_ISMOCK] = it }
            perAppTz?.let { prefs[Keys.XPOSED_PER_APP_TZ] = it }
            perAppBypass?.let { prefs[Keys.XPOSED_PER_APP_BYPASS] = it }
        }
        syncXposedBridge()
    }

    /**
     * Syncs lightweight config into a SharedPreferences file for LSPosed XSharedPreferences.
     * On non-root devices, MODE_WORLD_READABLE throws SecurityException which is safely ignored.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("WorldReadableFiles")
    suspend fun syncXposedBridge(city: City? = null, active: Boolean? = null) {
        val s = getCurrentSettings()
        val prefs = try {
            context.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            return // Normal on non-root devices without LSPosed New XSharedPreferences
        } catch (_: Throwable) {
            return
        }
        prefs.edit().apply {
            active?.let { putBoolean("active", it) }
            if (city != null) {
                val sim = MccTable.forCountry(city.countryCode)
                putString("lat", city.lat.toString())
                putString("lng", city.lng.toString())
                putString("tz", city.timeZoneId)
                putString("cc", sim.countryIso)
                putString("mcc_mnc", sim.mcc + sim.mnc)
                putString("operator", sim.operatorName)
            }
            putBoolean("hide_sim", s.xposedHideSim)
            putBoolean("hide_ismock", s.xposedHideIsMock)
            putBoolean("per_app_tz", s.syncTimeZone && s.xposedPerAppTz)
            putBoolean("per_app_bypass", s.xposedPerAppBypass)
            putString("bypass_csv", s.bypassPackages.joinToString(","))
            apply()
        }
    }

    private fun decodeIds(raw: String?): List<Long>? {
        if (raw == null) return null
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    companion object {
        const val XPOSED_PREFS_NAME = "notremock_xposed_bridge"
    }
}
