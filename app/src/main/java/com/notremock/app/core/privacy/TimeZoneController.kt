package com.notremock.app.core.privacy

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import com.notremock.app.data.prefs.TimeZoneMode
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.TimeZone

enum class ShizukuStatus {
    READY,
    NEEDS_PERMISSION,
    NOT_RUNNING
}

sealed class TimeZoneApplyResult {
    data class Applied(val timeZoneId: String) : TimeZoneApplyResult()
    data class RequiresManual(val timeZoneId: String) : TimeZoneApplyResult()
    data class Failed(val reason: String) : TimeZoneApplyResult()
}

object TimeZoneController {

    fun isSystemTimeZoneMatching(targetTimeZoneId: String): Boolean {
        return TimeZone.getDefault().id.equals(targetTimeZoneId, ignoreCase = true)
    }

    fun isAutoTimeZoneEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
        }.getOrDefault(true)
    }

    fun checkShizukuStatus(): ShizukuStatus {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.NEEDS_PERMISSION
            }
        } catch (_: Throwable) {
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    fun applyTimeZone(timeZoneId: String, mode: TimeZoneMode): TimeZoneApplyResult {
        if (timeZoneId.isBlank()) return TimeZoneApplyResult.Failed("Empty timezone")
        return when (mode) {
            TimeZoneMode.MANUAL -> TimeZoneApplyResult.RequiresManual(timeZoneId)
            TimeZoneMode.SHIZUKU -> applyViaShizuku(timeZoneId)
            TimeZoneMode.ROOT -> applyViaRoot(timeZoneId)
        }
    }

    private fun applyViaShizuku(timeZoneId: String): TimeZoneApplyResult {
        if (checkShizukuStatus() != ShizukuStatus.READY) {
            return TimeZoneApplyResult.RequiresManual(timeZoneId)
        }
        return try {
            val rawBinder = SystemServiceHelper.getSystemService(Context.ALARM_SERVICE)
                ?: return TimeZoneApplyResult.RequiresManual(timeZoneId)
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.app.IAlarmManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val alarmService = asInterface.invoke(null, wrapped)
                ?: return TimeZoneApplyResult.RequiresManual(timeZoneId)
            val setTz = alarmService.javaClass.getMethod("setTimeZone", String::class.java)
            setTz.invoke(alarmService, timeZoneId)
            TimeZoneApplyResult.Applied(timeZoneId)
        } catch (t: Throwable) {
            TimeZoneApplyResult.Failed(t.message ?: "Shizuku error")
        }
    }

    private fun applyViaRoot(timeZoneId: String): TimeZoneApplyResult {
        val safeTz = timeZoneId.replace(Regex("[^A-Za-z0-9_/+\\-]"), "")
        return try {
            val cmd = "settings put global auto_time_zone 0 && setprop persist.sys.timezone $safeTz && service call alarm 3 s16 $safeTz"
            val proc = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val exit = proc.waitFor()
            if (exit == 0) {
                TimeZoneApplyResult.Applied(safeTz)
            } else {
                TimeZoneApplyResult.RequiresManual(safeTz)
            }
        } catch (_: Throwable) {
            TimeZoneApplyResult.RequiresManual(safeTz)
        }
    }
}
