package app.locationlab.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import app.locationlab.R
import app.locationlab.simulation.Coordinate
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface CurrentLocation {
    data class Fix(val point: Coordinate, val approximate: Boolean) : CurrentLocation

    enum class Failure(@StringRes val message: Int) : CurrentLocation {
        PermissionDenied(R.string.location_permission_denied),
        Disabled(R.string.location_disabled),
        Unavailable(R.string.location_unavailable),
    }
}

internal fun Context.hasForegroundLocation(): Boolean =
    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

internal suspend fun readCurrentLocation(context: Context): CurrentLocation {
    if (!context.hasForegroundLocation()) return CurrentLocation.Failure.PermissionDenied
    val precise = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    val manager = context.getSystemService(LocationManager::class.java)
    if (!LocationManagerCompat.isLocationEnabled(manager)) return CurrentLocation.Failure.Disabled

    val enabled = manager.getProviders(true)
    val preferred = buildList {
        if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in enabled) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (precise && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
    }
    val network = listOf(LocationManager.NETWORK_PROVIDER).filter { it in enabled && it !in preferred }
    val point = withTimeoutOrNull(10_000) { awaitFix(context, manager, preferred) }
        ?: withTimeoutOrNull(5_000) { awaitFix(context, manager, network) }
    return when {
        point != null -> CurrentLocation.Fix(point, approximate = !precise)
        !context.hasForegroundLocation() -> CurrentLocation.Failure.PermissionDenied
        else -> CurrentLocation.Failure.Unavailable
    }
}

@SuppressLint("MissingPermission") // The caller checks foreground permissions and chooses allowed providers.
private suspend fun awaitFix(context: Context, manager: LocationManager, providers: List<String>): Coordinate? {
    if (providers.isEmpty()) return null
    val requests = providers.associateWith { CancellationSignal() }
    return try {
        suspendCancellableCoroutine { continuation ->
            var remaining = requests.size
            val executor = ContextCompat.getMainExecutor(context)
            fun complete(location: Location?) {
                if (!continuation.isActive) return
                val point = location?.takeUnless(LocationCompat::isMock)?.let { fix ->
                    runCatching { Coordinate(fix.latitude, fix.longitude) }.getOrNull()
                }
                remaining--
                if (point != null || remaining == 0) continuation.resume(point)
            }
            for ((provider, signal) in requests) {
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor, ::complete)
                } catch (_: SecurityException) {
                    complete(null)
                } catch (_: IllegalArgumentException) {
                    complete(null)
                }
            }
        }
    } finally {
        requests.values.forEach { it.cancel() }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
