package com.notremock.app.data.model

import java.util.TimeZone
import kotlin.math.abs

data class City(
    val id: Long,
    val nameEn: String,
    val nameFa: String,
    val admin1En: String,
    val countryCode: String,
    val countryEn: String,
    val countryFa: String,
    val lat: Double,
    val lng: Double,
    val timeZoneId: String,
    val isCapital: Boolean,
    val population: Long
) {
    fun displayName(isPersian: Boolean): String =
        if (isPersian && nameFa.isNotBlank()) nameFa else nameEn

    fun displayCountry(isPersian: Boolean): String =
        if (isPersian && countryFa.isNotBlank()) countryFa else countryEn

    fun displaySubtitle(isPersian: Boolean): String {
        val region = if (!isPersian && admin1En.isNotBlank() && !admin1En.equals(nameEn, ignoreCase = true)) {
            "$admin1En, ${displayCountry(false)}"
        } else {
            displayCountry(isPersian)
        }
        return "$region • ${formattedUtcOffset()}"
    }

    fun formattedUtcOffset(): String {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val offsetMs = tz.getOffset(System.currentTimeMillis())
        val totalMinutes = offsetMs / 60_000
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absMinutes = abs(totalMinutes)
        val hours = absMinutes / 60
        val minutes = absMinutes % 60
        return if (minutes == 0) {
            "UTC$sign%02d:00".format(hours)
        } else {
            "UTC$sign%02d:%02d".format(hours, minutes)
        }
    }
}
