package ro.infotb.modern.location

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

/** Great-circle distance in metres. */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = (b.lat - a.lat) * PI / 180
    val dLng = (b.lng - a.lng) * PI / 180
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * asin(sqrt(h))
}

/**
 * One-shot access to the device position, used to order dashboard cards by
 * distance. Asks for "while in use" permission the first time it is needed.
 */
expect class PlatformLocation() {
    /** False where the platform has no location service (desktop). */
    fun isSupported(): Boolean

    /** The current position, or null when unavailable or permission was denied. */
    suspend fun current(): GeoPoint?
}
