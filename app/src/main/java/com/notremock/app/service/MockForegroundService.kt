package com.notremock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.notremock.app.MainActivity
import com.notremock.app.R
import com.notremock.app.core.location.MockErrorType
import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.core.location.MockState
import com.notremock.app.core.privacy.OemGuideHelper
import com.notremock.app.data.db.CityDatabaseHelper
import com.notremock.app.data.model.City
import com.notremock.app.data.prefs.PanicAction
import com.notremock.app.data.prefs.PreferencesRepository
import com.notremock.app.widget.NotreMockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: MockLocationEngine
    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var cityDb: CityDatabaseHelper
    private var injectionJob: Job? = null

    private var baseLat: Double = 0.0
    private var baseLng: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
        prefsRepo = PreferencesRepository(this)
        cityDb = CityDatabaseHelper(this)
        createNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val cityId = intent.getLongExtra(EXTRA_CITY_ID, -1L)
                serviceScope.launch { startMocking(cityId) }
            }
            ACTION_RANDOMIZE -> {
                serviceScope.launch { randomizeCurrentCity() }
            }
            ACTION_PAUSE -> {
                val pkg = intent.getStringExtra(EXTRA_BYPASS_PKG)
                serviceScope.launch { pauseMocking(pkg) }
            }
            ACTION_RESUME -> {
                serviceScope.launch { resumeMocking() }
            }
            ACTION_PANIC -> {
                serviceScope.launch { handlePanic() }
            }
            ACTION_STOP -> {
                serviceScope.launch { stopMocking() }
            }
        }
        return START_STICKY
    }

    private suspend fun startMocking(requestedCityId: Long) {
        if (!MockLocationEngine.hasLocationPermission(this)) {
            MockState.update { it.copy(isRunning = false, errorType = MockErrorType.LOCATION_PERMISSION_MISSING) }
            stopSelf()
            return
        }
        if (!OemGuideHelper.isDeviceLocationEnabled(this)) {
            MockState.update { it.copy(isRunning = false, errorType = MockErrorType.DEVICE_LOCATION_OFF) }
            stopSelf()
            return
        }
        if (!MockLocationEngine.isMockAppSelected(this)) {
            MockState.update { it.copy(isRunning = false, errorType = MockErrorType.MOCK_APP_NOT_SELECTED) }
            stopSelf()
            return
        }

        val settings = prefsRepo.getCurrentSettings()
        val cityId = if (requestedCityId > 0) requestedCityId else settings.selectedCityId
        val city = cityDb.getCityById(cityId) ?: cityDb.searchCities("", 1).firstOrNull()
        if (city == null) {
            stopSelf()
            return
        }

        val (randLat, randLng) = MockLocationEngine.randomizeInCity(city.lat, city.lng, settings.cityRadiusKm)
        baseLat = randLat
        baseLng = randLng

        val startRes = engine.startProviders()
        if (startRes.isFailure) {
            handleRevokedOrFailure()
            return
        }

        MockState.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                pausedByPackage = null,
                activeCity = city,
                currentLat = baseLat,
                currentLng = baseLng,
                errorType = MockErrorType.NONE
            )
        }
        prefsRepo.setMockingActiveState(true, city)
        startForegroundCompat(buildServiceNotification(city, pausedBy = null, stealth = settings.stealthNotification))
        refreshTile()
        launchInjectionLoop()
    }

    private suspend fun randomizeCurrentCity() {
        val current = MockState.state.value
        val city = current.activeCity ?: return
        val settings = prefsRepo.getCurrentSettings()
        val (randLat, randLng) = MockLocationEngine.randomizeInCity(city.lat, city.lng, settings.cityRadiusKm)
        baseLat = randLat
        baseLng = randLng
        if (current.isRunning && !current.isPaused) {
            engine.injectLocation(baseLat, baseLng, settings.altitudeMeters.toDouble(), settings.jitterMeters)
        }
    }

    private suspend fun pauseMocking(bypassPkg: String?) {
        val current = MockState.state.value
        if (!current.isRunning || current.isPaused) return
        injectionJob?.cancel()
        engine.stopProviders()
        val settings = prefsRepo.getCurrentSettings()
        MockState.update { it.copy(isPaused = true, pausedByPackage = bypassPkg) }
        current.activeCity?.let { city ->
            updateNotification(buildServiceNotification(city, pausedBy = bypassPkg ?: "App", stealth = settings.stealthNotification))
        }
        refreshTile()
    }

    private suspend fun resumeMocking() {
        val current = MockState.state.value
        if (!current.isRunning || !current.isPaused) return
        val startRes = engine.startProviders()
        if (startRes.isFailure) {
            handleRevokedOrFailure()
            return
        }
        val settings = prefsRepo.getCurrentSettings()
        MockState.update { it.copy(isPaused = false, pausedByPackage = null) }
        current.activeCity?.let { city ->
            updateNotification(buildServiceNotification(city, pausedBy = null, stealth = settings.stealthNotification))
        }
        refreshTile()
        launchInjectionLoop()
    }

    private suspend fun handlePanic() {
        val settings = prefsRepo.getCurrentSettings()
        if (settings.panicAction == PanicAction.JUMP_FALLBACK_CITY) {
            startMocking(settings.fallbackCityId)
        } else {
            stopMocking()
        }
    }

    private suspend fun stopMocking() {
        injectionJob?.cancel()
        engine.stopProviders()
        prefsRepo.setMockingActiveState(false)
        MockState.reset()
        refreshTile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchInjectionLoop() {
        injectionJob?.cancel()
        injectionJob = serviceScope.launch {
            while (isActive) {
                val state = MockState.state.value
                if (!state.isRunning || state.isPaused) break
                val settings = prefsRepo.getCurrentSettings()
                val res = engine.injectLocation(
                    baseLat,
                    baseLng,
                    settings.altitudeMeters.toDouble(),
                    settings.jitterMeters
                )
                if (res.isFailure) {
                    handleRevokedOrFailure()
                    break
                }
                delay(settings.updateIntervalMs.toLong().coerceIn(400L, 10_000L))
            }
        }
    }

    private suspend fun handleRevokedOrFailure() {
        val settings = prefsRepo.getCurrentSettings()
        injectionJob?.cancel()
        engine.stopProviders()
        prefsRepo.setMockingActiveState(false)
        MockState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                pausedByPackage = null,
                errorType = MockErrorType.MOCK_APP_NOT_SELECTED
            )
        }
        if (settings.watchdogEnabled) {
            postWatchdogLeakAlert()
        }
        refreshTile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postWatchdogLeakAlert() {
        val lctx = ContextCompat.getContextForLanguage(this)
        val openApp = PendingIntent.getActivity(
            this, 200,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield_pin)
            .setContentTitle(lctx.getString(R.string.notif_leak_title))
            .setContentText(lctx.getString(R.string.notif_leak_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(ALERT_NOTIF_ID, notif)
    }

    private fun buildServiceNotification(city: City, pausedBy: String?, stealth: Boolean): Notification {
        val lctx = ContextCompat.getContextForLanguage(this)
        val isPersian = lctx.resources.configuration.locales[0]?.language == "fa"

        val openApp = PendingIntent.getActivity(
            this, 100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val panicIntent = PendingIntent.getService(
            this, 101,
            Intent(this, MockForegroundService::class.java).setAction(ACTION_PANIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            pausedBy != null -> lctx.getString(R.string.notif_paused_title, pausedBy)
            stealth -> lctx.getString(R.string.notif_active_stealth_title)
            else -> lctx.getString(R.string.notif_active_title, city.displayName(isPersian))
        }
        val body = when {
            pausedBy != null -> lctx.getString(R.string.notif_paused_body)
            stealth -> lctx.getString(R.string.notif_active_body, city.formattedUtcOffset())
            else -> lctx.getString(R.string.notif_active_body, city.timeZoneId)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_shield_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, lctx.getString(R.string.notif_action_stop), panicIntent)

        if (pausedBy != null) {
            val resumeIntent = PendingIntent.getService(
                this, 102,
                Intent(this, MockForegroundService::class.java).setAction(ACTION_RESUME),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, lctx.getString(R.string.notif_action_resume), resumeIntent)
        }
        return builder.build()
    }

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SERVICE_NOTIF_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_NOTIF_ID, notification)
        }
    }

    private fun refreshTile() {
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, MockTileService::class.java))
        }
        NotreMockWidgetProvider.refreshAll(this)
    }

    override fun onDestroy() {
        injectionJob?.cancel()
        engine.stopProviders()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_SERVICE = "notremock_service"
        const val CHANNEL_ALERTS = "notremock_alerts"
        const val SERVICE_NOTIF_ID = 4101
        const val ALERT_NOTIF_ID = 4102
        const val BOOT_NOTIF_ID = 4103

        const val ACTION_START = "com.notremock.app.ACTION_START"
        const val ACTION_STOP = "com.notremock.app.ACTION_STOP"
        const val ACTION_PANIC = "com.notremock.app.ACTION_PANIC"
        const val ACTION_PAUSE = "com.notremock.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.notremock.app.ACTION_RESUME"
        const val ACTION_RANDOMIZE = "com.notremock.app.ACTION_RANDOMIZE"

        const val EXTRA_CITY_ID = "extra_city_id"
        const val EXTRA_BYPASS_PKG = "extra_bypass_pkg"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val lctx = ContextCompat.getContextForLanguage(context)
                val nm = context.getSystemService(NotificationManager::class.java)
                val serviceChannel = NotificationChannel(
                    CHANNEL_SERVICE,
                    lctx.getString(R.string.notif_channel_service),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                val alertChannel = NotificationChannel(
                    CHANNEL_ALERTS,
                    lctx.getString(R.string.notif_channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(serviceChannel)
                nm.createNotificationChannel(alertChannel)
            }
        }

        fun start(context: Context, cityId: Long) {
            val intent = Intent(context, MockForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CITY_ID, cityId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }

        fun panic(context: Context) {
            val intent = Intent(context, MockForegroundService::class.java).apply {
                action = ACTION_PANIC
            }
            runCatching { context.startService(intent) }
        }

        fun randomize(context: Context) {
            val intent = Intent(context, MockForegroundService::class.java).apply {
                action = ACTION_RANDOMIZE
            }
            runCatching { context.startService(intent) }
        }
    }
}
