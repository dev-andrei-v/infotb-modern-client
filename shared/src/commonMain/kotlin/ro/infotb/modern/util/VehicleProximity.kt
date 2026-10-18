package ro.infotb.modern.util

import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * How close the next approaching vehicle is to a stop, measured along the route.
 *
 * @property stopsAway   stops the vehicle still has to reach, counting the target (1 = next stop is yours)
 * @property distanceMeters distance along the route from the vehicle to the stop
 * @property progress    0..1 position of the vehicle inside the last [PROXIMITY_WINDOW_STOPS] stops before the target
 * @property vehicleLat / vehicleLng the chosen vehicle's position, for a map marker
 */
data class VehicleProximity(
    val stopsAway: Int,
    val distanceMeters: Int,
    val progress: Double,
    val vehicleLat: Double,
    val vehicleLng: Double
)

/** How many stops before the target the progress track covers. */
const val PROXIMITY_WINDOW_STOPS = 5

/** A vehicle further than this from the polyline is not on this route. */
private const val MAX_OFF_ROUTE_METERS = 250.0

private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Picks the nearest vehicle that has not yet passed [targetStopId] and measures
 * its distance along [routePolyline]. Returns null when there is no such
 * vehicle or the inputs are too thin to say anything.
 */
fun computeVehicleProximity(
    routePolyline: List<Pair<Double, Double>>,
    stops: List<Stop>,
    targetStopId: Long,
    vehicles: List<VehiclePosition>
): VehicleProximity? {
    if (routePolyline.size < 2 || vehicles.isEmpty()) return null
    val target = stops.firstOrNull { it.id == targetStopId } ?: return null

    val route = RouteGeometry(routePolyline)
    val targetAlong = route.project(target.lat, target.lng)?.along ?: return null

    // Stops projected onto the route, in route order
    val stopAlongs = stops.mapNotNull { s -> route.project(s.lat, s.lng)?.along }.sorted()

    // Nearest vehicle that is still before the stop
    val candidate = vehicles
        .mapNotNull { v -> route.project(v.lat, v.lng)?.let { p -> Triple(v, p.along, p.offset) } }
        .filter { (_, along, offset) -> offset <= MAX_OFF_ROUTE_METERS && along <= targetAlong }
        .maxByOrNull { it.second } ?: return null
    val (vehicle, vehicleAlong, _) = candidate

    val stopsAway = stopAlongs.count { it > vehicleAlong && it <= targetAlong }.coerceAtLeast(1)

    // Window start: the PROXIMITY_WINDOW_STOPS-th stop before the target, or the route start
    val before = stopAlongs.filter { it < targetAlong }
    val windowStart = if (before.size >= PROXIMITY_WINDOW_STOPS) before[before.size - PROXIMITY_WINDOW_STOPS] else 0.0
    val span = (targetAlong - windowStart).coerceAtLeast(1.0)
    val progress = ((vehicleAlong - windowStart) / span).coerceIn(0.0, 1.0)

    return VehicleProximity(
        stopsAway = stopsAway,
        distanceMeters = (targetAlong - vehicleAlong).toInt().coerceAtLeast(0),
        progress = progress,
        vehicleLat = vehicle.lat,
        vehicleLng = vehicle.lng
    )
}

/** Result of projecting a point onto the route: distance along it and perpendicular offset. */
private data class Projection(val along: Double, val offset: Double)

/**
 * Flat-earth (equirectangular) geometry over a polyline. Bucharest-scale
 * distances make the approximation error negligible for this purpose.
 */
private class RouteGeometry(points: List<Pair<Double, Double>>) {
    private val refLat = points.first().first
    private val cosLat = cos(refLat * PI / 180)

    // Each vertex in metres (x east, y north) and cumulative distance to it
    private val xs = DoubleArray(points.size)
    private val ys = DoubleArray(points.size)
    private val cum = DoubleArray(points.size)

    init {
        points.forEachIndexed { i, (lat, lng) ->
            xs[i] = toX(lng)
            ys[i] = toY(lat)
            cum[i] = if (i == 0) 0.0 else cum[i - 1] + dist(xs[i - 1], ys[i - 1], xs[i], ys[i])
        }
    }

    fun project(lat: Double, lng: Double): Projection? {
        val px = toX(lng)
        val py = toY(lat)
        var best: Projection? = null
        for (i in 0 until xs.size - 1) {
            val ax = xs[i]; val ay = ys[i]
            val bx = xs[i + 1]; val by = ys[i + 1]
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val offset = dist(px, py, cx, cy)
            if (best == null || offset < best.offset) {
                best = Projection(along = cum[i] + t * sqrt(len2), offset = offset)
            }
        }
        return best
    }

    private fun toX(lng: Double) = lng * PI / 180 * EARTH_RADIUS_M * cosLat
    private fun toY(lat: Double) = lat * PI / 180 * EARTH_RADIUS_M
    private fun dist(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
