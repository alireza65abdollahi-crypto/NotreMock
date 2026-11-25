package com.notremock.app.xposed

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import com.notremock.app.data.prefs.PreferencesRepository
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.TimeZone

/**
 * Embedded LSPosed / Xposed module for Rooted devices.
 * Provides real per-app bypass (including background services like Find My Device),
 * TelephonyManager SIM/Cell spoofing, isMock() hiding, and per-app TimeZone spoofing.
 */
class NotreMockXposedEntry : IXposedHookLoadPackage {

    private val prefs by lazy {
        XSharedPreferences("com.notremock.app", PreferencesRepository.XPOSED_PREFS_NAME)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return

        // Self-hook so UI knows LSPosed module is active
        if (pkg == "com.notremock.app") {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "com.notremock.app.xposed.XposedStatus",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            }
            return
        }

        if (isSystemExempt(pkg)) return

        runCatching { hookLocationFlags(lpparam.classLoader, pkg) }
        runCatching { hookTelephony(lpparam.classLoader, pkg) }
        runCatching { hookTimeZone(lpparam.classLoader, pkg) }
    }

    private fun refreshPrefs(): Boolean {
        prefs.reload()
        return prefs.getBoolean("active", false)
    }

    private fun isBypassed(pkg: String): Boolean {
        if (!prefs.getBoolean("per_app_bypass", true)) return false
        val csv = prefs.getString("bypass_csv", "") ?: ""
        return csv.split(',').any { it.trim() == pkg }
    }

    private fun hookLocationFlags(cl: ClassLoader, pkg: String) {
        val hideMock = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!refreshPrefs() || isBypassed(pkg)) return
                if (prefs.getBoolean("hide_ismock", false)) {
                    param.result = false
                }
            }
        }
        XposedHelpers.findAndHookMethod(Location::class.java, "isFromMockProvider", hideMock)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            XposedHelpers.findAndHookMethod(Location::class.java, "isMock", hideMock)
        }

        // In per-app bypass mode, if real GPS is kept on or mocked per-app, spoof getLastKnownLocation coordinates
        XposedHelpers.findAndHookMethod(
            LocationManager::class.java,
            "getLastKnownLocation",
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!refreshPrefs() || isBypassed(pkg)) return
                    val loc = param.result as? Location ?: return
                    val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return
                    val lng = prefs.getString("lng", null)?.toDoubleOrNull() ?: return
                    loc.latitude = lat
                    loc.longitude = lng
                }
            }
        )
    }

    private fun hookTelephony(cl: ClassLoader, pkg: String) {
        val hookCountry = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!refreshPrefs() || isBypassed(pkg) || !prefs.getBoolean("hide_sim", false)) return
                val cc = prefs.getString("cc", "") ?: ""
                if (cc.isNotBlank()) param.result = cc
            }
        }
        val hookOperatorCode = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!refreshPrefs() || isBypassed(pkg) || !prefs.getBoolean("hide_sim", false)) return
                val code = prefs.getString("mcc_mnc", "") ?: ""
                if (code.isNotBlank()) param.result = code
            }
        }
        val hookOperatorName = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!refreshPrefs() || isBypassed(pkg) || !prefs.getBoolean("hide_sim", false)) return
                val name = prefs.getString("operator", "") ?: ""
                if (name.isNotBlank()) param.result = name
            }
        }
        val hookEmptyCells = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!refreshPrefs() || isBypassed(pkg) || !prefs.getBoolean("hide_sim", false)) return
                param.result = emptyList<Any>()
            }
        }

        val tm = TelephonyManager::class.java
        XposedHelpers.findAndHookMethod(tm, "getSimCountryIso", hookCountry)
        XposedHelpers.findAndHookMethod(tm, "getNetworkCountryIso", hookCountry)
        XposedHelpers.findAndHookMethod(tm, "getSimOperator", hookOperatorCode)
        XposedHelpers.findAndHookMethod(tm, "getNetworkOperator", hookOperatorCode)
        XposedHelpers.findAndHookMethod(tm, "getSimOperatorName", hookOperatorName)
        XposedHelpers.findAndHookMethod(tm, "getNetworkOperatorName", hookOperatorName)
        XposedHelpers.findAndHookMethod(tm, "getAllCellInfo", hookEmptyCells)
    }

    private fun hookTimeZone(cl: ClassLoader, pkg: String) {
        XposedHelpers.findAndHookMethod(
            TimeZone::class.java,
            "getDefault",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!refreshPrefs() || isBypassed(pkg) || !prefs.getBoolean("per_app_tz", false)) return
                    val tzId = prefs.getString("tz", "") ?: ""
                    if (tzId.isNotBlank()) {
                        param.result = TimeZone.getTimeZone(tzId)
                    }
                }
            }
        )
    }

    private fun isSystemExempt(pkg: String): Boolean {
        return pkg == "android" || pkg == "com.android.systemui" || pkg == "com.android.phone"
    }

    @Suppress("unused")
    private fun logError(t: Throwable) {
        XposedBridge.log(t)
    }
}
