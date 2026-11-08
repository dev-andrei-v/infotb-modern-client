package ro.infotb.modern.util

import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.location.GeoPoint
import ro.infotb.modern.location.distanceMeters

/** Original route indices, ordered by distance with stable ties. */
fun nearbyStopIndices(stops: List<Stop>, location: GeoPoint?, count: Int, radius: Int): List<Int> {
    if (location == null || count <= 0) return emptyList()
    return stops.indices.mapNotNull { index ->
        val stop = stops[index]
        if (!stop.lat.isFinite() || !stop.lng.isFinite() || stop.lat !in -90.0..90.0 ||
            stop.lng !in -180.0..180.0 || (stop.lat == 0.0 && stop.lng == 0.0)) return@mapNotNull null
        val distance = distanceMeters(location, GeoPoint(stop.lat, stop.lng))
        if (distance.isFinite() && distance <= radius) index to distance else null
    }.sortedWith(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
        .take(count.coerceAtMost(5)).map { it.first }
}
