package com.notremock.app

import com.notremock.app.core.location.MockLocationEngine
import com.notremock.app.data.db.CityDatabaseHelper
import com.notremock.app.data.model.City
import com.notremock.app.data.model.MccTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CityAndLocationTest {

    @Test
    fun normalizeSearch_normalizesPersianAndArabicVariants() {
        val arabic = CityDatabaseHelper.normalizeSearch("  تهران\u200c ي ك آ أ إ ة ")
        assertEquals("تهران ی ک ا ا ا ه", arabic)
    }

    @Test
    fun applyJitter_staysWithinExpectedBounds() {
        val baseLat = 52.5200
        val baseLng = 13.4050
        repeat(50) {
            val (lat, lng) = MockLocationEngine.applyJitter(baseLat, baseLng, 10)
            assertTrue(abs(lat - baseLat) < 0.001)
            assertTrue(abs(lng - baseLng) < 0.001)
        }
    }

    @Test
    fun randomizeInCity_zeroRadiusReturnsExactCenter() {
        val (lat, lng) = MockLocationEngine.randomizeInCity(35.6892, 51.3890, 0f)
        assertEquals(35.6892, lat, 1e-6)
        assertEquals(51.3890, lng, 1e-6)
    }

    @Test
    fun mccTable_returnsExpectedCarrierOrFallback() {
        val de = MccTable.forCountry("de")
        assertEquals("262", de.mcc)
        assertEquals("de", de.countryIso)

        val unknown = MccTable.forCountry("ZZ")
        assertEquals("310", unknown.mcc)
        assertEquals("zz", unknown.countryIso)
    }

    @Test
    fun cityDisplayName_prefersPersianWhenLocaleIsPersian() {
        val tehran = City(
            id = 112931L,
            nameEn = "Tehran",
            nameFa = "تهران",
            admin1En = "Tehran",
            countryCode = "IR",
            countryEn = "Iran",
            countryFa = "ایران",
            lat = 35.69439,
            lng = 51.42151,
            timeZoneId = "Asia/Tehran",
            isCapital = true,
            population = 7153309L
        )
        assertEquals("تهران", tehran.displayName(isPersian = true))
        assertEquals("Tehran", tehran.displayName(isPersian = false))
        assertEquals("ایران", tehran.displayCountry(isPersian = true))
    }
}
