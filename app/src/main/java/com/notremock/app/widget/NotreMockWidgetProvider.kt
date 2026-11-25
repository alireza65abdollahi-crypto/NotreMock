package com.notremock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.notremock.app.MainActivity
import com.notremock.app.R
import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.core.location.MockState
import com.notremock.app.core.privacy.OemGuideHelper
import com.notremock.app.data.db.CityDatabaseHelper
import com.notremock.app.data.prefs.PreferencesRepository
import com.notremock.app.service.MockForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotreMockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) {
                    val views = buildViews(context)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TOGGLE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val current = MockState.state.value
                    if (current.isRunning) {
                        MockForegroundService.stop(context)
                    } else {
                        val canStart = MockLocationEngine.hasLocationPermission(context) &&
                            OemGuideHelper.isDeviceLocationEnabled(context) &&
                            MockLocationEngine.isMockAppSelected(context)
                        if (canStart) {
                            val settings = PreferencesRepository(context).getCurrentSettings()
                            MockForegroundService.start(context, settings.selectedCityId)
                        } else {
                            context.startActivity(
                                Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                    refreshAllSync(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.notremock.app.ACTION_WIDGET_TOGGLE"

        fun refreshAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                refreshAllSync(context)
            }
        }

        private suspend fun refreshAllSync(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NotreMockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val lctx = ContextCompat.getContextForLanguage(context)
            val isPersian = lctx.resources.configuration.locales[0]?.language == "fa"
            val runtime = MockState.state.value
            val city = runtime.activeCity ?: run {
                val settings = PreferencesRepository(context).getCurrentSettings()
                CityDatabaseHelper(context).getCityById(settings.selectedCityId)
            }

            val statusText = when {
                runtime.isRunning && runtime.isPaused -> lctx.getString(R.string.status_paused, runtime.pausedByPackage ?: "")
                runtime.isRunning -> lctx.getString(R.string.status_active)
                else -> lctx.getString(R.string.status_inactive)
            }

            val cityText = if (city != null) {
                "${city.displayName(isPersian)} • ${city.formattedUtcOffset()}"
            } else {
                lctx.getString(R.string.no_city_selected)
            }

            val openAppIntent = PendingIntent.getActivity(
                context, 501,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val toggleIntent = PendingIntent.getBroadcast(
                context, 502,
                Intent(context, NotreMockWidgetProvider::class.java).setAction(ACTION_WIDGET_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_notremock).apply {
                setTextViewText(R.id.widget_status, statusText)
                setTextViewText(R.id.widget_city, cityText)
                setTextViewText(
                    R.id.widget_toggle_btn,
                    lctx.getString(if (runtime.isRunning) R.string.widget_stop else R.string.widget_start)
                )
                setInt(
                    R.id.widget_toggle_btn,
                    "setBackgroundResource",
                    if (runtime.isRunning) R.drawable.widget_btn_stop else R.drawable.widget_btn_start
                )
                setInt(
                    R.id.widget_icon,
                    "setColorFilter",
                    when {
                        runtime.isRunning && runtime.isPaused -> Color.parseColor("#F59E0B")
                        runtime.isRunning -> Color.parseColor("#10B981")
                        else -> Color.parseColor("#94A3B8")
                    }
                )
                setOnClickPendingIntent(R.id.widget_root, openAppIntent)
                setOnClickPendingIntent(R.id.widget_toggle_btn, toggleIntent)
            }
        }
    }
}
