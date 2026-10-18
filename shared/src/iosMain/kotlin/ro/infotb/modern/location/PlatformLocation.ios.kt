package ro.infotb.modern.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * One-shot CLLocationManager wrapper. Lives for the app's lifetime so the
 * delegate stays alive across the asynchronous permission prompt.
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformLocation actual constructor() {

    private val manager = CLLocationManager()
    private var pendingAuth: CompletableDeferred<Boolean>? = null
    private var pendingFix: CompletableDeferred<GeoPoint?>? = null

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = manager.authorizationStatus
            if (status != kCLAuthorizationStatusNotDetermined) {
                pendingAuth?.complete(isAuthorized(status))
                pendingAuth = null
            }
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val last = didUpdateLocations.lastOrNull() as? CLLocation
            val point = last?.coordinate?.useContents { GeoPoint(latitude, longitude) }
            pendingFix?.complete(point)
            pendingFix = null
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            pendingFix?.complete(null)
            pendingFix = null
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    actual fun isSupported(): Boolean = true

    actual suspend fun current(): GeoPoint? {
        if (!ensureAuthorized()) return null
        val deferred = CompletableDeferred<GeoPoint?>()
        pendingFix = deferred
        manager.requestLocation()
        return withTimeoutOrNull(8_000) { deferred.await() }.also {
            pendingFix = null
        }
    }

    private suspend fun ensureAuthorized(): Boolean {
        val status = manager.authorizationStatus
        if (isAuthorized(status)) return true
        if (status != kCLAuthorizationStatusNotDetermined) return false
        val deferred = CompletableDeferred<Boolean>()
        pendingAuth = deferred
        manager.requestWhenInUseAuthorization()
        return withTimeoutOrNull(60_000) { deferred.await() } ?: false
    }

    private fun isAuthorized(status: CLAuthorizationStatus): Boolean =
        status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
}
