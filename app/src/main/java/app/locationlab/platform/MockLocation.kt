package app.locationlab.platform

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import app.locationlab.simulation.Coordinate

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

internal class MockLocation(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val installed = mutableListOf<String>()

    // These compile-time constants have the same values as the legacy Criteria constants.
    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    fun open() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers.filter { it in manager.allProviders }) {
            val gps = provider == LocationManager.GPS_PROVIDER
            if (Build.VERSION.SDK_INT >= 31) {
                val properties = ProviderProperties.Builder()
                    .setHasSatelliteRequirement(gps)
                    .setHasNetworkRequirement(!gps)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .build()
                manager.addTestProvider(provider, properties)
            } else {
                manager.addTestProvider(
                    provider, !gps, gps, false, false, false, false, false,
                    ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE,
                )
            }
            installed += provider
            manager.setTestProviderEnabled(provider, true)
        }
        check(installed.isNotEmpty())
    }

    fun update(point: Coordinate) {
        for (provider in installed) {
            manager.setTestProviderLocation(provider, Location(provider).apply {
                latitude = point.latitude
                longitude = point.longitude
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
        }
    }

    fun close(): Boolean {
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
        return clean
    }
}
