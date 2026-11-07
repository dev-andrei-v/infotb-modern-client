package ro.infotb.modern.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.location.GeoPoint

class NearbyStopsTest {
    private val location = GeoPoint(44.4268, 26.1025)
    private val stops = listOf(
        Stop(1, 44.4308, 26.1025, "Farther"),
        Stop(2, 44.4268, 26.1025, "Closest"),
        Stop(3, 44.4268, 26.1025, "Other platform"),
        Stop(4, 0.0, 0.0, "Missing location"),
        Stop(5, Double.NaN, 26.0, "Invalid location")
    )
    @Test fun sortsByDistanceAndPreservesOriginalIndicesAndTies() {
        assertEquals(listOf(1, 2, 0), nearbyStopIndices(stops, location, 5, 1000))
    }
    @Test fun respectsRadiusCountAndDisabledState() {
        assertEquals(listOf(1, 2), nearbyStopIndices(stops, location, 5, 200))
        assertEquals(listOf(1), nearbyStopIndices(stops, location, 1, 1000))
        assertTrue(nearbyStopIndices(stops, location, 0, 1000).isEmpty())
        assertTrue(nearbyStopIndices(stops, null, 3, 1000).isEmpty())
    }
}
