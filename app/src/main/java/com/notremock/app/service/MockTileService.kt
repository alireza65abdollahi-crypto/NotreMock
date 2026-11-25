package com.notremock.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.notremock.app.MainActivity
import com.notremock.app.R
import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.core.location.MockState
import com.notremock.app.core.privacy.OemGuideHelper
import com.notremock.app.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MockTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch {
            MockState.state.collectLatest {
                updateTileUi()
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val current = MockState.state.value
        if (current.isRunning) {
            runCatching { MockForegroundService.panic(applicationContext) }
            updateTileUi()
            return
        }

        val ready = MockLocationEngine.hasLocationPermission(applicationContext) &&
            OemGuideHelper.isDeviceLocationEnabled(applicationContext) &&
            MockLocationEngine.isMockAppSelected(applicationContext)

        if (!ready) {
            openAppToStartOrSetup(autoStart = false)
            return
        }

        scope.launch {
            val settings = PreferencesRepository(applicationContext).getCurrentSettings()
            val started = runCatching {
                MockForegroundService.start(applicationContext, settings.selectedCityId)
            }.isSuccess

            if (!started) {
                // Android 14/15 may forbid starting location FGS directly from background QS tile
                openAppToStartOrSetup(autoStart = true)
            } else {
                updateTileUi()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppToStartOrSetup(autoStart: Boolean) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (autoStart) putExtra(MainActivity.EXTRA_AUTO_START_ON_OPEN, true)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    applicationContext,
                    601,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure {
            runCatching { startActivity(intent) }
        }
    }

    private fun updateTileUi() {
        val tile = qsTile ?: return
        val lctx = ContextCompat.getContextForLanguage(this)
        val locales = lctx.resources.configuration.locales
        val isPersian = !locales.isEmpty && locales[0]?.language == "fa"
        val current = MockState.state.value
        if (current.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = current.activeCity?.displayName(isPersian) ?: lctx.getString(R.string.status_active)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = lctx.getString(R.string.status_inactive)
            }
        }
        runCatching { tile.updateTile() }
    }

    override fun onDestroy() {
        listenJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
