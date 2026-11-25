package com.notremock.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MockLocationEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var fusedClient: FusedLocationProviderClient? = null
    private var providersRegistered = false

    private val registeredProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    fun startProviders(): Result<Unit> = runCatching {
        if (!hasLocationPermission(context)) {
            throw SecurityException("Location permission missing")
        }

        registeredProviders.forEach { provider ->
            runCatching { locationManager.removeTestProvider(provider) }
            addTestProviderCompat(provider)
            runCatching { locationManager.setTestProviderEnabled(provider, true) }
        }
        providersRegistered = true

        initFusedIfAvailable()
        @SuppressLint("MissingPermission")
        runCatching { fusedClient?.setMockMode(true) }
    }

    fun injectLocation(
        lat: Double,
        lng: Double,
        altitudeMeters: Double,
        jitterMeters: Int
    ): Result<Unit> = runCatching {
        if (!providersRegistered) {
            startProviders().getOrThrow()
        }
        val (finalLat, finalLng) = applyJitter(lat, lng, jitterMeters)
        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val alt = altitudeMeters + Random.nextDouble(-0.4, 0.4)
        val accuracy = (3.5f + Random.nextFloat() * 4.5f)

        registeredProviders.forEach { provider ->
            val loc = buildLocation(provider, finalLat, finalLng, alt, accuracy, now, elapsedNanos)
            locationManager.setTestProviderLocation(provider, loc)
        }

        @SuppressLint("MissingPermission")
        runCatching {
            val fusedLoc = buildLocation(LocationManager.GPS_PROVIDER, finalLat, finalLng, alt, accuracy, now, elapsedNanos)
            fusedClient?.setMockLocation(fusedLoc)
        }

        MockState.update {
            it.copy(
                currentLat = finalLat,
                currentLng = finalLng,
                errorType = MockErrorType.NONE
            )
        }
    }

    fun stopProviders() {
        providersRegistered = false
        registeredProviders.forEach { provider ->
            runCatching { locationManager.setTestProviderEnabled(provider, false) }
            runCatching { locationManager.removeTestProvider(provider) }
        }
        @SuppressLint("MissingPermission")
        runCatching { fusedClient?.setMockMode(false) }
    }

    private fun initFusedIfAvailable() {
        if (fusedClient != null) return
        val available = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
        if (available) {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun addTestProviderCompat(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val accuracy = if (provider == LocationManager.GPS_PROVIDER) {
                ProviderProperties.ACCURACY_FINE
            } else {
                ProviderProperties.ACCURACY_COARSE
            }
            val props = ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(accuracy)
                .build()
            locationManager.addTestProvider(provider, props)
        } else {
            val accuracy = if (provider == LocationManager.GPS_PROVIDER) 1 else 2
            locationManager.addTestProvider(
                provider,
                false, false, false, false,
                true, true, true,
                1, accuracy
            )
        }
    }

    private fun buildLocation(
        provider: String,
        lat: Double,
        lng: Double,
        alt: Double,
        accuracy: Float,
        timeMs: Long,
        elapsedNanos: Long
    ): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            altitude = alt
            this.accuracy = accuracy
            bearing = Random.nextFloat() * 360f
            speed = 0.0f
            time = timeMs
            elapsedRealtimeNanos = elapsedNanos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 10f
                speedAccuracyMetersPerSecond = 0.5f
                verticalAccuracyMeters = 3f
            }
        }
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun isMockAppSelected(context: Context): Boolean {
            // Check AppOps first
            val appOpsAllowed = runCatching {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.packageName
                    )
                } else {
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)

            if (appOpsAllowed) return true

            // Fallback probe: some OEMs report MODE_DEFAULT until test provider probe
            return runCatching {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val probe = "notremock_probe"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    lm.addTestProvider(
                        probe,
                        ProviderProperties.Builder()
                            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                            .setAccuracy(ProviderProperties.ACCURACY_FINE)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    lm.addTestProvider(probe, false, false, false, false, false, false, false, 1, 1)
                }
                lm.removeTestProvider(probe)
                true
            }.getOrDefault(false)
        }

        fun applyJitter(lat: Double, lng: Double, maxRadiusMeters: Int): Pair<Double, Double> {
            if (maxRadiusMeters <= 0) return lat to lng
            val u = Random.nextDouble()
            val v = Random.nextDouble()
            val distance = maxRadiusMeters * sqrt(u)
            val angle = 2.0 * PI * v
            val dLat = (distance * cos(angle)) / METERS_PER_DEGREE_LAT
            val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
            val dLng = (distance * sin(angle)) / (METERS_PER_DEGREE_LAT * cosLat)
            return (lat + dLat).coerceIn(-89.9, 89.9) to normalizeLongitude(lng + dLng)
        }

        fun randomizeInCity(lat: Double, lng: Double, radiusKm: Float): Pair<Double, Double> {
            if (radiusKm <= 0f) return lat to lng
            return applyJitter(lat, lng, (radiusKm * 1000f).toInt())
        }

        private fun normalizeLongitude(lng: Double): Double {
            var value = lng
            while (value > 180.0) value -= 360.0
            while (value < -180.0) value += 360.0
            return value
        }
    }
}
