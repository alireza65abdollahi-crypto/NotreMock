package com.notremock.app.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notremock.app.NotreMockApp
import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.core.location.MockRuntimeState
import com.notremock.app.core.location.MockState
import com.notremock.app.core.privacy.ForegroundAppDetector
import com.notremock.app.core.privacy.IpCheckState
import com.notremock.app.core.privacy.OemGuideHelper
import com.notremock.app.core.privacy.PrivacyAuditReport
import com.notremock.app.core.privacy.PrivacyAuditor
import com.notremock.app.core.privacy.SetupDiagnostics
import com.notremock.app.core.privacy.ShizukuStatus
import com.notremock.app.core.privacy.SystemIntentHelper
import com.notremock.app.core.privacy.TimeZoneApplyResult
import com.notremock.app.core.privacy.TimeZoneController
import com.notremock.app.data.db.CityDatabaseHelper
import com.notremock.app.data.model.City
import com.notremock.app.data.prefs.AppLanguage
import com.notremock.app.data.prefs.AppSettings
import com.notremock.app.data.prefs.PanicAction
import com.notremock.app.data.prefs.PreferencesRepository
import com.notremock.app.data.prefs.ThemeMode
import com.notremock.app.data.prefs.TimeZoneMode
import com.notremock.app.service.MockForegroundService
import com.notremock.app.widget.NotreMockWidgetProvider
import com.notremock.app.xposed.XposedStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val cityDb = CityDatabaseHelper(ctx)
    private val prefsRepo = PreferencesRepository(ctx)

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val runtimeState: StateFlow<MockRuntimeState> = MockState.state

    private val _selectedCity = MutableStateFlow<City?>(null)
    val selectedCity: StateFlow<City?> = _selectedCity.asStateFlow()

    private val _favoriteCities = MutableStateFlow<List<City>>(emptyList())
    val favoriteCities: StateFlow<List<City>> = _favoriteCities.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<City>>(emptyList())
    val searchResults: StateFlow<List<City>> = _searchResults.asStateFlow()

    private val _totalCitiesCount = MutableStateFlow(0)
    val totalCitiesCount: StateFlow<Int> = _totalCitiesCount.asStateFlow()

    private val _auditReport = MutableStateFlow(PrivacyAuditReport())
    val auditReport: StateFlow<PrivacyAuditReport> = _auditReport.asStateFlow()

    private val _ipCheckState = MutableStateFlow<IpCheckState>(IpCheckState.Idle)
    val ipCheckState: StateFlow<IpCheckState> = _ipCheckState.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isBatteryExempt = MutableStateFlow(true)
    val isBatteryExempt: StateFlow<Boolean> = _isBatteryExempt.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isXposedActive = MutableStateFlow(false)
    val isXposedActive: StateFlow<Boolean> = _isXposedActive.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _tzFeedback = MutableStateFlow<TimeZoneApplyResult?>(null)
    val tzFeedback: StateFlow<TimeZoneApplyResult?> = _tzFeedback.asStateFlow()

    private val _isSettingsLoaded = MutableStateFlow(false)
    val isSettingsLoaded: StateFlow<Boolean> = _isSettingsLoaded.asStateFlow()

    private val _diagnostics = MutableStateFlow(
        SetupDiagnostics(
            hasLocationPermission = false,
            isDeviceLocationOn = true,
            isDeveloperOptionsOn = false,
            isMockAppSelected = false,
            isBatteryExempt = true,
            hasNotificationPermission = true
        )
    )
    val diagnostics: StateFlow<SetupDiagnostics> = _diagnostics.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _totalCitiesCount.value = cityDb.getTotalCount()
            _searchResults.value = cityDb.searchCities("")
            _isXposedActive.value = XposedStatus.isModuleActive()
        }

        viewModelScope.launch {
            settings.collectLatest { s ->
                _isSettingsLoaded.value = true
                val city = withContext(Dispatchers.IO) {
                    cityDb.getCityById(s.selectedCityId) ?: cityDb.searchCities("", 1).firstOrNull()
                }
                _selectedCity.value = city
                val favAndRecent = (s.favoriteCityIds + s.recentCityIds).distinct()
                _favoriteCities.value = withContext(Dispatchers.IO) {
                    cityDb.getCitiesByIds(favAndRecent)
                }
                refreshStatus(s.syncTimeZone)
            }
        }
    }

    fun refreshStatus(syncTimeZoneOverride: Boolean? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetCity = runtimeState.value.activeCity ?: _selectedCity.value
            val locPerm = MockLocationEngine.hasLocationPermission(ctx)
            val batteryExempt = SystemIntentHelper.isIgnoringBatteryOptimizations(ctx)
            val audit = PrivacyAuditor.runAudit(ctx, targetCity, syncTimeZoneOverride ?: settings.value.syncTimeZone)
            val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            _hasLocationPermission.value = locPerm
            _isBatteryExempt.value = batteryExempt
            _isAccessibilityEnabled.value = ForegroundAppDetector.hasUsageAccessPermission(ctx)
            _shizukuStatus.value = TimeZoneController.checkShizukuStatus()
            _auditReport.value = audit
            _diagnostics.value = SetupDiagnostics(
                hasLocationPermission = locPerm,
                isDeviceLocationOn = OemGuideHelper.isDeviceLocationEnabled(ctx),
                isDeveloperOptionsOn = OemGuideHelper.isDeveloperOptionsEnabled(ctx),
                isMockAppSelected = audit.isMockAppSelected,
                isBatteryExempt = batteryExempt,
                hasNotificationPermission = notifPerm
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            _searchResults.value = cityDb.searchCities(query, limit = 100)
        }
    }

    fun selectCity(city: City, startIfRunning: Boolean = true) {
        viewModelScope.launch {
            prefsRepo.selectCity(city)
            _selectedCity.value = city
            _ipCheckState.value = IpCheckState.Idle
            if (startIfRunning && runtimeState.value.isRunning) {
                MockForegroundService.start(ctx, city.id)
            }
            NotreMockWidgetProvider.refreshAll(ctx)
            refreshStatus()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    fun toggleMocking() {
        val current = runtimeState.value
        if (current.isRunning) {
            MockForegroundService.stop(ctx)
        } else {
            val cityId = _selectedCity.value?.id ?: settings.value.selectedCityId
            MockForegroundService.start(ctx, cityId)
        }
    }

    fun triggerKillSwitch() {
        MockForegroundService.panic(ctx)
    }

    fun randomizePoint() {
        MockForegroundService.randomize(ctx)
    }

    fun toggleFavorite(cityId: Long) {
        viewModelScope.launch { prefsRepo.toggleFavorite(cityId) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefsRepo.updateLanguage(language)
            NotreMockApp.applyAppLocale(language)
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.updateTheme(mode) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateAutoStartOnBoot(enabled) }
    }

    fun setBypassEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateBypassEnabled(enabled) }
    }

    fun toggleBypassPackage(pkg: String) {
        viewModelScope.launch { prefsRepo.toggleBypassPackage(pkg) }
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val apps = resolved
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ctx.packageName) return@mapNotNull null
                    val label = ri.loadLabel(pm)?.toString() ?: pkg
                    InstalledAppInfo(pkg, label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    fun updateRealism(jitterMeters: Int, cityRadiusKm: Float, intervalMs: Int, altitudeMeters: Int) {
        viewModelScope.launch {
            prefsRepo.updateRealism(jitterMeters, cityRadiusKm, intervalMs, altitudeMeters)
        }
    }

    fun updateKillSwitch(watchdog: Boolean, stealth: Boolean, panicAction: PanicAction, fallbackCityId: Long) {
        viewModelScope.launch {
            prefsRepo.updateKillSwitch(watchdog, stealth, panicAction, fallbackCityId)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateBiometricLock(enabled) }
    }

    fun setTimeZoneMode(mode: TimeZoneMode) {
        viewModelScope.launch { prefsRepo.updateTimeZoneMode(mode) }
    }

    fun setSyncTimeZone(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSyncTimeZone(enabled)
            refreshStatus(enabled)
        }
    }

    fun applyCityTimeZone() {
        val city = runtimeState.value.activeCity ?: _selectedCity.value ?: return
        val mode = settings.value.timeZoneMode
        viewModelScope.launch(Dispatchers.IO) {
            val res = TimeZoneController.applyTimeZone(city.timeZoneId, mode)
            _tzFeedback.value = res
            refreshStatus()
        }
    }

    fun clearTzFeedback() {
        _tzFeedback.value = null
    }

    fun setAllowIpCheck(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateAllowIpCheck(enabled)
            if (!enabled) _ipCheckState.value = IpCheckState.Idle
        }
    }

    fun checkIpMatch() {
        if (!settings.value.allowIpCheck) return
        val cc = (runtimeState.value.activeCity ?: _selectedCity.value)?.countryCode ?: return
        _ipCheckState.value = IpCheckState.Loading
        viewModelScope.launch {
            _ipCheckState.value = PrivacyAuditor.checkPublicIpCountry(cc)
        }
    }

    fun updateXposedSettings(
        hideSim: Boolean? = null,
        hideIsMock: Boolean? = null,
        perAppTz: Boolean? = null,
        perAppBypass: Boolean? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateXposedSettings(hideSim, hideIsMock, perAppTz, perAppBypass)
        }
    }
}
