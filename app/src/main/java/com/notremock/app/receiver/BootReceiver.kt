package com.notremock.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.notremock.app.MainActivity
import com.notremock.app.R
import com.notremock.app.data.db.CityDatabaseHelper
import com.notremock.app.data.prefs.PreferencesRepository
import com.notremock.app.service.MockForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = PreferencesRepository(context)
                val settings = repo.getCurrentSettings()
                if (!settings.autoStartOnBoot) return@launch

                MockForegroundService.createNotificationChannels(context)
                try {
                    MockForegroundService.start(context, settings.selectedCityId)
                } catch (_: Throwable) {
                    // Android 14/15 may block background location FGS on BOOT_COMPLETED without BG location permission
                    postResumePromptNotification(context, settings.selectedCityId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postResumePromptNotification(context: Context, cityId: Long) {
        val lctx = ContextCompat.getContextForLanguage(context)
        val locales = lctx.resources.configuration.locales
        val isFa = !locales.isEmpty && locales[0]?.language == "fa"
        val cityName = CityDatabaseHelper(context).getCityById(cityId)?.displayName(isFa) ?: "Last City"

        val startIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_AUTO_START_ON_OPEN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, 301, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, MockForegroundService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield_pin)
            .setContentTitle(lctx.getString(R.string.notif_boot_fallback_title))
            .setContentText(lctx.getString(R.string.notif_boot_fallback_body, cityName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(MockForegroundService.BOOT_NOTIF_ID, notif)
    }
}
