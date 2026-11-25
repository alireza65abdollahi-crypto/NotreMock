package com.notremock.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.notremock.app.R
import com.notremock.app.core.location.MockState
import com.notremock.app.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MockTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        updateTileUi()
    }

    override fun onClick() {
        super.onClick()
        val current = MockState.state.value
        if (current.isRunning) {
            MockForegroundService.panic(this)
        } else {
            scope.launch {
                val settings = PreferencesRepository(applicationContext).getCurrentSettings()
                MockForegroundService.start(applicationContext, settings.selectedCityId)
            }
        }
        updateTileUi()
    }

    private fun updateTileUi() {
        val tile = qsTile ?: return
        val current = MockState.state.value
        if (current.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = current.activeCity?.nameEn ?: getString(R.string.status_active)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.status_inactive)
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
