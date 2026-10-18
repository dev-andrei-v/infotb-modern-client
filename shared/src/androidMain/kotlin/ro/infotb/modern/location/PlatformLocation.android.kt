package ro.infotb.modern.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ro.infotb.modern.AndroidContext
import kotlin.coroutines.resume

/**
 * Bridge for the runtime permission prompt, which only an Activity can show.
 * MainActivity installs a requester at startup.
 */
object AndroidLocationPermission {
    @Volatile
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}

actual class PlatformLocation actual constructor() {

    actual fun isSupported(): Boolean = true

    actual suspend fun current(): GeoPoint? {
        val context = AndroidContext.appContext
        if (!hasPermission(context) && !requestPermission()) return null
        return withTimeoutOrNull(8_000) { readLocation(context) }
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
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = providers.first()
            val fresh = suspendCancellableCoroutine<GeoPoint?> { cont ->
                try {
                    manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                        if (cont.isActive) cont.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (fresh != null) return fresh
        }

        // Fall back to the most recent fix any provider has
        return providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
