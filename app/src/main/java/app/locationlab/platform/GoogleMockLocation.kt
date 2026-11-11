package app.locationlab.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/** Google Play's fused location API is separate from Android's test providers. */
internal class GoogleMockLocation private constructor(
    private val client: FusedLocationProviderClient,
    private val enabled: Task<Void>,
) {
    private var lastFix: Task<Void>? = null
    private var cleanup: Task<Void>? = null

    @SuppressLint("InlinedApi") // FUSED_PROVIDER is the inlined provider name on all Android versions.
    fun update(fix: Location) {
        // Skip a tick while a fix is in flight, so a slow service cannot queue stale positions.
        if (cleanup != null || !enabled.isSuccessful || lastFix?.isSuccessful == false) return
        val first = lastFix == null
        val location = Location(fix).apply { provider = LocationManager.FUSED_PROVIDER }
        val sent = try {
            client.setMockLocation(location)
        } catch (error: SecurityException) {
            Tasks.forException<Void>(error)
        }
        lastFix = sent
        if (first) sent.addOnSuccessListener { Log.i(TAG, "Simulated fix accepted") }
        sent.addOnCompleteListener {
            if (!it.isSuccessful) {
                Log.w(TAG, "Simulated fix failed")
                close()
            }
        }
    }

    fun close(): Task<Void> {
        cleanup?.let { return it }
        // Wait even if Stop was pressed while enabling the service or sending a fix.
        val disabled = Tasks.whenAllComplete(lastFix ?: enabled).continueWithTask {
            if (enabled.isSuccessful) setMockMode(client, false) else Tasks.forResult<Void>(null)
        }
        cleanup = disabled
        lastCleanup = disabled
        disabled.addOnCompleteListener {
            if (enabled.isSuccessful) {
                if (it.isSuccessful) Log.i(TAG, "Mock mode disabled")
                else Log.w(TAG, "Mock mode cleanup failed")
            }
        }
        return disabled
    }

    companion object {
        private const val TAG = "VLocGoogleLocation"

        // Called on the service main thread. A restart must follow the previous cleanup.
        private var lastCleanup: Task<Void> = Tasks.forResult(null)

        fun open(context: Context): GoogleMockLocation? {
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) return null
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val enabled = Tasks.whenAllComplete(lastCleanup).continueWithTask { setMockMode(client, true) }
            enabled.addOnCompleteListener {
                if (it.isSuccessful) Log.i(TAG, "Mock mode enabled")
                else Log.w(TAG, "Mock mode unavailable")
            }
            return GoogleMockLocation(client, enabled)
        }

        // The developer option grants mock access through AppOps and may be changed during a run.
        private fun setMockMode(client: FusedLocationProviderClient, enabled: Boolean): Task<Void> = try {
            client.setMockMode(enabled)
        } catch (error: SecurityException) {
            Tasks.forException(error)
        }
    }
}
