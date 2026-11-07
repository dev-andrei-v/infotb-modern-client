package ro.infotb.modern.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ro.infotb.modern.AndroidContext
import kotlin.coroutines.resume

/** Bridge for the runtime permission prompt, which only an Activity can show. */
object AndroidLocationPermission {
    @Volatile
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}

actual class PlatformLocation actual constructor() {
    actual fun isSupported(): Boolean = true

    actual suspend fun current(): GeoPoint? = withContext(Dispatchers.Main.immediate) {
        val context = AndroidContext.appContext
        if (!hasPermission(context) && !requestPermission()) return@withContext null
        readLocation(context)
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun requestPermission(): Boolean {
        val requester = AndroidLocationPermission.requester ?: return false
        val result = CompletableDeferred<Boolean>()
        requester { granted -> result.complete(granted) }
        return result.await()
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLocation(context: Context): GeoPoint? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val finePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        // Network/fused also work indoors. Do not wait for an outdoor GPS fix first,
        // or request GPS with only approximate-location permission on older devices.
        val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it != LocationManager.GPS_PROVIDER || finePermission }
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        fun recentCachedLocation(): Location? = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.isRecentFix() }
            .maxByOrNull { it.elapsedRealtimeNanos }

        recentCachedLocation()?.let { return GeoPoint(it.latitude, it.longitude) }
        val fresh = firstAvailableLocation(providers.map { provider ->
            suspend {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(
                            manager, provider, cancellation, ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (continuation.isActive) continuation.resume(location?.takeIf { it.isRecentFix() })
                        }
                    } catch (_: SecurityException) {
                        if (continuation.isActive) continuation.resume(null)
                    } catch (_: IllegalArgumentException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        })
        // Keep fallback outside the timeout: a provider can update its cache even
        // when its current-location callback never arrives.
        return (fresh ?: recentCachedLocation())?.let { GeoPoint(it.latitude, it.longitude) }
    }
}

private fun Location.isRecentFix(): Boolean {
    val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
    return ageNanos in 0..120_000_000_000L &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
