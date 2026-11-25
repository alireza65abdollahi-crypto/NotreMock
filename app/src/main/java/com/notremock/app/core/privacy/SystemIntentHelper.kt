package com.notremock.app.core.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object SystemIntentHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openDeveloperOptions(context: Context) {
        launchSafe(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun openDateAndTimeSettings(context: Context) {
        launchSafe(context, Intent(Settings.ACTION_DATE_SETTINGS)) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun openLocationSettings(context: Context) {
        launchSafe(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        launchSafe(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun openWirelessSettings(context: Context) {
        launchSafe(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun requestBatteryExemption(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launchSafe(context, intent) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launchSafe(context, intent) { Intent(Settings.ACTION_SETTINGS) }
    }

    private fun launchSafe(context: Context, primary: Intent, fallback: () -> Intent) {
        val first = primary.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            context.startActivity(first)
        } catch (_: Throwable) {
            runCatching {
                context.startActivity(fallback().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
    }
}
