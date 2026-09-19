package us.i3u.hermesstudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/** What the server asked for in `location.requested`. */
data class LocationRequest(
    val id: String,
    val sessionId: String,
    val purpose: String,
    /** "precise" or "coarse". */
    val accuracy: String,
    val timeoutMs: Long,
)

/** A fix in the exact shape `location.respond` expects (`coordinateSystem` must be `wgs84`). */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val timestamp: Long,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("accuracyMeters", accuracyMeters)
        .put("coordinateSystem", "wgs84")
        .put("timestamp", timestamp)
        .apply {
            altitudeMeters?.let { put("altitudeMeters", it) }
            speedMetersPerSecond?.let { put("speedMetersPerSecond", it) }
        }
}

sealed interface LocationResult {
    data class Success(val fix: LocationFix) : LocationResult
    data object Denied : LocationResult
    /** [code] is one of the server's known codes: location_permission_denied, location_unavailable, location_timeout, location_failed. */
    data class Error(val code: String) : LocationResult
}

/** Builds the `location.respond` payload for a result. */
fun locationResponsePayload(sessionId: String, requestId: String, result: LocationResult): JSONObject {
    val payload = JSONObject().put("session_id", sessionId).put("location_request_id", requestId)
    when (result) {
        is LocationResult.Success -> payload.put("status", "success").put("location", result.fix.toJson())
        LocationResult.Denied -> payload.put("status", "denied")
        is LocationResult.Error -> payload.put("status", "error").put("error", JSONObject().put("code", result.code))
    }
    return payload
}

/**
 * Reads the device position with the platform [LocationManager] (no Play
 * services dependency). The caller must already hold a location permission;
 * this only converts the fix into the wire format.
 */
object MobileLocation {

    fun hasPermission(context: Context, precise: Boolean): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (precise) fine else fine || coarse
    }

    fun permissionsFor(precise: Boolean): Array<String> =
        if (precise) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * One current fix, or an error code. Tries a fresh fix first (bounded by
     * [timeoutMs]) and falls back to the last known position from any provider.
     */
    suspend fun currentFix(context: Context, precise: Boolean, timeoutMs: Long): LocationResult {
        if (!hasPermission(context, precise)) return LocationResult.Error("location_permission_denied")
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.Error("location_unavailable")
        val providers = listOf(
            if (precise) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER,
            if (precise) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return LocationResult.Error("location_unavailable")

        val fresh = withTimeoutOrNull(timeoutMs.coerceIn(1_000L, 60_000L)) {
            requestSingle(context, manager, providers.first())
        }
        val location = fresh ?: providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        return if (location == null) LocationResult.Error(if (fresh == null) "location_timeout" else "location_unavailable")
        else LocationResult.Success(location.toFix())
    }

    private suspend fun requestSingle(context: Context, manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    @Suppress("MissingPermission")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (failure: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (failure: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else 0.0,
        timestamp = if (time > 0) time else System.currentTimeMillis(),
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
    )
}
