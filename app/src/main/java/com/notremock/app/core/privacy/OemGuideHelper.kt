package com.notremock.app.core.privacy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

enum class OemBrand {
    SAMSUNG,
    XIAOMI,
    HUAWEI,
    OPPO_REALME_ONEPLUS,
    STOCK_OTHER;

    companion object {
        fun detect(): OemBrand {
            val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
            return when {
                "samsung" in m -> SAMSUNG
                "xiaomi" in m || "redmi" in m || "poco" in m -> XIAOMI
                "huawei" in m || "honor" in m -> HUAWEI
                "oppo" in m || "realme" in m || "oneplus" in m -> OPPO_REALME_ONEPLUS
                else -> STOCK_OTHER
            }
        }
    }
}

data class SetupDiagnostics(
    val hasLocationPermission: Boolean,
    val isDeviceLocationOn: Boolean,
    val isDeveloperOptionsOn: Boolean,
    val isMockAppSelected: Boolean,
    val isBatteryExempt: Boolean,
    val hasNotificationPermission: Boolean
) {
    val isCoreReady: Boolean
        get() = hasLocationPermission && isDeviceLocationOn && isDeveloperOptionsOn && isMockAppSelected
}

object OemGuideHelper {

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)
    }

    fun isDeviceLocationEnabled(context: Context): Boolean {
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.isLocationEnabled(lm)
        }.getOrDefault(true)
    }

    fun openAboutPhoneSettings(context: Context) {
        val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openOemAutostartOrBatterySettings(context: Context) {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
        for (cn in candidates) {
            val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) {
                val launched = runCatching { context.startActivity(intent) }.isSuccess
                if (launched) return
            }
        }
        SystemIntentHelper.openAppDetails(context)
    }
}
