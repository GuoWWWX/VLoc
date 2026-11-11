package app.locationlab.platform

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import app.locationlab.simulation.Coordinate
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

@Suppress("DEPRECATION")
fun Context.canMockLocation(): Boolean {
    val ops = getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
    } else {
        ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

internal class MockLocation(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val installed = mutableListOf<String>()
    private val fixExtras = Bundle().apply { putInt("satellites", 10) }
    private var google: GoogleMockLocation? = null
    private var googleCleanup: Task<Void>? = null

    // These compile-time constants have the same values as the legacy Criteria constants.
    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    fun open() {
        // Criteria-based clients can use fused independently of GPS/network.
        // Only replace providers present on this device; passive relays their updates.
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER)
        for (provider in providers.filter { it in manager.allProviders }) {
            val gps = provider == LocationManager.GPS_PROVIDER
            val network = provider == LocationManager.NETWORK_PROVIDER
            val accuracy = if (network) ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE
            val powerUsage = if (gps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW
            if (Build.VERSION.SDK_INT >= 31) {
                val properties = ProviderProperties.Builder()
                    .setHasSatelliteRequirement(gps)
                    .setHasNetworkRequirement(network)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setAccuracy(accuracy)
                    .setPowerUsage(powerUsage)
                    .build()
                manager.addTestProvider(provider, properties)
            } else {
                manager.addTestProvider(
                    provider, network, gps, false, false, true, true, true, powerUsage, accuracy,
                )
            }
            installed += provider
            manager.setTestProviderEnabled(provider, true)
        }
        check(installed.isNotEmpty())
        googleCleanup = null
        google = GoogleMockLocation.open(context)
    }

    fun update(point: Coordinate) {
        // Passive listeners can receive the same simulated fix from any of these providers.
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = point.latitude
            longitude = point.longitude
            accuracy = 1.2f
            altitude = 0.0
            verticalAccuracyMeters = 1f
            speed = 0f
            speedAccuracyMetersPerSecond = 1.2f
            bearing = 0f
            bearingAccuracyDegrees = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            extras = fixExtras
        }
        for (provider in installed) {
            fix.provider = provider
            manager.setTestProviderLocation(provider, fix)
        }
        google?.update(fix)
    }

    fun close(): Task<Boolean> {
        google?.let { googleCleanup = it.close() }
        google = null
        var clean = true
        val iterator = installed.iterator()
        while (iterator.hasNext()) {
            try {
                manager.removeTestProvider(iterator.next())
                iterator.remove()
            } catch (_: RuntimeException) {
                clean = false
            }
        }
        val pending = googleCleanup ?: return Tasks.forResult(clean)
        if (pending.isComplete) return Tasks.forResult(clean && pending.isSuccessful)
        return pending.continueWith { clean && it.isSuccessful }
    }
}
