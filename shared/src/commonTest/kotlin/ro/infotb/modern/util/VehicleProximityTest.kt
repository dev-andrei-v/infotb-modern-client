package ro.infotb.modern.util

import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleProximityTest {

    // A straight east-west route at latitude 44.43 (Bucharest). Near this
    // latitude one degree of longitude is ~79.5 km, so 0.001 deg ≈ 79.5 m.
    private val lat = 44.43
    private val route = (0..10).map { i -> lat to (26.0 + i * 0.001) }

    // Stops every 2 vertices: ids 1..6 at lng 26.000, 26.002, ... 26.010
    private val stops = (0..5).map { i ->
        Stop(id = (i + 1).toLong(), lat = lat, lng = 26.0 + i * 0.002, name = "S${i + 1}")
    }

    @Test
    fun picksNearestVehicleBeforeTheStopAndCountsRemainingStops() {
        val vehicles = listOf(
            VehiclePosition(id = 1, lat = lat, lng = 26.0005), // far back
            VehiclePosition(id = 2, lat = lat, lng = 26.0045), // between S3 (26.004) and S4 (26.006)
            VehiclePosition(id = 3, lat = lat, lng = 26.0095)  // already past S5 (target)
        )
        val result = computeVehicleProximity(route, stops, targetStopId = 5, vehicles = vehicles)
        assertNotNull(result)
        // Remaining: S4 and S5 → 2 stops away
        assertEquals(2, result.stopsAway)
        // 26.008 - 26.0045 = 0.0035 deg ≈ 278 m
        assertTrue(result.distanceMeters in 260..300, "distance was ${result.distanceMeters}")
        assertEquals(26.0045, result.vehicleLng)
        assertTrue(result.progress in 0.0..1.0)
    }

    @Test
    fun vehicleAtTheStopIsOneStopAwayWithZeroDistance() {
        val vehicles = listOf(VehiclePosition(id = 1, lat = lat, lng = 26.008))
        val result = computeVehicleProximity(route, stops, targetStopId = 5, vehicles = vehicles)
        assertNotNull(result)
        assertEquals(1, result.stopsAway)
        assertEquals(0, result.distanceMeters)
        assertEquals(1.0, result.progress)
    }

    @Test
    fun ignoresVehiclesThatAlreadyPassedTheStop() {
        val vehicles = listOf(VehiclePosition(id = 1, lat = lat, lng = 26.0095))
        assertNull(computeVehicleProximity(route, stops, targetStopId = 5, vehicles = vehicles))
    }

    @Test
    fun ignoresVehiclesFarOffTheRoute() {
        // ~1.1 km north of the line
        val vehicles = listOf(VehiclePosition(id = 1, lat = lat + 0.01, lng = 26.003))
        assertNull(computeVehicleProximity(route, stops, targetStopId = 5, vehicles = vehicles))
    }

    @Test
    fun progressGrowsAsTheVehicleApproaches() {
        val far = computeVehicleProximity(route, stops, 6, listOf(VehiclePosition(1, lat, 26.001)))
        val near = computeVehicleProximity(route, stops, 6, listOf(VehiclePosition(1, lat, 26.009)))
        assertNotNull(far); assertNotNull(near)
        assertTrue(near.progress > far.progress)
    }

    @Test
    fun returnsNullWithoutVehiclesOrUnknownStop() {
        assertNull(computeVehicleProximity(route, stops, 5, emptyList()))
        assertNull(computeVehicleProximity(route, stops, 99, listOf(VehiclePosition(1, lat, 26.001))))
    }
}
