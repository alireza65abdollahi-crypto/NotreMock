package com.notremock.app.core.privacy

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.data.model.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PrivacyAuditReport(
    val isMockAppSelected: Boolean = false,
    val isTimeZoneMatching: Boolean = false,
    val isAutoTimeZoneOff: Boolean = false,
    val isWifiScanningOff: Boolean = false,
    val isBleScanningOff: Boolean = false,
    val isCellularProtected: Boolean = false,
    val score: Int = 0
)

sealed class IpCheckState {
    data object Idle : IpCheckState()
    data object Loading : IpCheckState()
    data class Result(val ipCountryCode: String, val matchesCity: Boolean) : IpCheckState()
    data object Error : IpCheckState()
}

object PrivacyAuditor {

    fun runAudit(context: Context, targetCity: City?): PrivacyAuditReport {
        val mockSelected = MockLocationEngine.isMockAppSelected(context)
        val tzMatch = targetCity != null && TimeZoneController.isSystemTimeZoneMatching(targetCity.timeZoneId)
        val autoTzOff = !TimeZoneController.isAutoTimeZoneEnabled(context)
        val wifiScanOff = isWifiScanningDisabled(context)
        val bleScanOff = isBleScanningDisabled(context)
        val cellularSafe = isAirplaneModeOn(context) || !hasActiveSim(context)

        var score = 0
        if (mockSelected) score += 25
        if (tzMatch) score += 20
        if (autoTzOff) score += 10
        if (wifiScanOff) score += 15
        if (bleScanOff) score += 10
        if (cellularSafe) score += 20

        return PrivacyAuditReport(
            isMockAppSelected = mockSelected,
            isTimeZoneMatching = tzMatch,
            isAutoTimeZoneOff = autoTzOff,
            isWifiScanningOff = wifiScanOff,
            isBleScanningOff = bleScanOff,
            isCellularProtected = cellularSafe,
            score = score.coerceIn(0, 100)
        )
    }

    @Suppress("DEPRECATION")
    private fun isWifiScanningDisabled(context: Context): Boolean {
        val settingOff = runCatching {
            Settings.Global.getInt(context.contentResolver, "wifi_scan_always_enabled", 0) == 0
        }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val apiOff = runCatching { wm?.isScanAlwaysAvailable == false }.getOrDefault(settingOff)
            return apiOff
        }
        return settingOff
    }

    private fun isBleScanningDisabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled", 0) == 0
        }.getOrDefault(false)
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        }.getOrDefault(false)
    }

    private fun hasActiveSim(context: Context): Boolean {
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.simState == TelephonyManager.SIM_STATE_READY
        }.getOrDefault(false)
    }

    /**
     * Opt-in only: queries public IP country API when explicitly triggered by the user.
     */
    suspend fun checkPublicIpCountry(targetCountryCode: String): IpCheckState = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.country.is/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val cc = json.optString("country", "").uppercase()
                if (cc.isBlank()) IpCheckState.Error
                else IpCheckState.Result(cc, cc.equals(targetCountryCode, ignoreCase = true))
            }
        } catch (_: Throwable) {
            IpCheckState.Error
        }
    }
}
