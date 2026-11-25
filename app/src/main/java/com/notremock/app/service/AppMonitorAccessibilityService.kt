package com.notremock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.notremock.app.core.location.MockState
import com.notremock.app.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Privacy-minimal AccessibilityMonitor:
 * - canRetrieveWindowContent="false" (cannot read screen text)
 * - Only inspects event.packageName on window state transitions to pause Mock GPS for bypassed apps.
 */
class AppMonitorAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefsRepo: PreferencesRepository
    private var debounceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefsRepo = PreferencesRepository(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (isIgnoredTransientPackage(pkg)) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500L)
            val runtime = MockState.state.value
            if (!runtime.isRunning) return@launch

            val settings = prefsRepo.getCurrentSettings()
            if (!settings.bypassEnabled) return@launch

            val shouldBypass = settings.bypassPackages.contains(pkg)
            if (shouldBypass && !runtime.isPaused) {
                val appLabel = resolveAppLabel(pkg)
                startService(
                    Intent(this@AppMonitorAccessibilityService, MockForegroundService::class.java).apply {
                        action = MockForegroundService.ACTION_PAUSE
                        putExtra(MockForegroundService.EXTRA_BYPASS_PKG, appLabel)
                    }
                )
            } else if (!shouldBypass && runtime.isPaused && pkg != packageName) {
                startService(
                    Intent(this@AppMonitorAccessibilityService, MockForegroundService::class.java).apply {
                        action = MockForegroundService.ACTION_RESUME
                    }
                )
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun isIgnoredTransientPackage(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imePackages = runCatching { imm?.enabledInputMethodList?.map { it.packageName } }.getOrNull()
        return imePackages?.contains(pkg) == true
    }

    private fun resolveAppLabel(pkg: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    companion object {
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AppMonitorAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
