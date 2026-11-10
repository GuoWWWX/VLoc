package app.locationlab.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import app.locationlab.simulation.Coordinate
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class PlaceMatch(val point: Coordinate, val name: String, val area: String)

internal fun parseCoordinateSearch(input: String): Coordinate? {
    val parts = input.trim().split(Regex("[,，\\s]+")).takeIf { it.size == 2 } ?: return null
    val latitude = parts[0].decimalCoordinate()?.takeIf { it in -90.0..90.0 } ?: return null
    val longitude = parts[1].decimalCoordinate()?.takeIf { it in -180.0..180.0 } ?: return null
    return Coordinate(latitude, longitude)
}

internal fun String.decimalCoordinate(): Double? = trim().replace('。', '.').replace('．', '.').toDoubleOrNull()

@Suppress("DEPRECATION")
internal suspend fun searchPlaces(context: Context, query: String): List<PlaceMatch>? =
    geocode(context, { geocoder, listener -> geocoder.getFromLocationName(query, 5, listener) },
        { it.getFromLocationName(query, 5) })?.mapNotNull { address ->
        if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
        val latitude = address.latitude.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
        val longitude = address.longitude.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
        val point = Coordinate(latitude, longitude)
        PlaceMatch(point, address.displayName() ?: query, address.area())
    }?.distinctBy { it.point }

@Suppress("DEPRECATION")
internal suspend fun reversePlace(context: Context, point: Coordinate): String? =
    geocode(context, { geocoder, listener -> geocoder.getFromLocation(point.latitude, point.longitude, 1, listener) },
        { it.getFromLocation(point.latitude, point.longitude, 1) })?.firstOrNull()?.displayName()

private suspend fun geocode(
    context: Context,
    modern: (Geocoder, Geocoder.GeocodeListener) -> Unit,
    legacy: (Geocoder) -> List<Address>?,
): List<Address>? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context)
    return try {
        withTimeoutOrNull(5_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { continuation ->
                    modern(geocoder, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { continuation.resume(addresses) }
                        override fun onError(errorMessage: String?) { continuation.resume(null) }
                    })
                }
            } else {
                withContext(Dispatchers.IO) { legacy(geocoder) }
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun Address.displayName(): String? = listOfNotNull(featureName, thoroughfare, subLocality, locality)
    .firstOrNull { it.isNotBlank() && it.any(Char::isLetter) }

private fun Address.area(): String = listOfNotNull(adminArea, locality, subLocality).distinct().joinToString(" ")
