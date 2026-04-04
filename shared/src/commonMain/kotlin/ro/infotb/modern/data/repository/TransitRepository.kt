package ro.infotb.modern.data.repository

import ro.infotb.modern.data.api.StbApi
import ro.infotb.modern.data.model.*

class TransitRepository(private val api: StbApi) {

    suspend fun getAllLines(): List<Line> {
        return api.getAllLines()
    }

    suspend fun getLineDetail(lineId: Long): LineDetail {
        return api.getLineDetail(lineId)
    }

    suspend fun getLineDirection(lineId: Long, direction: Direction): LineDetail {
        return api.getLineDirection(lineId, direction)
    }

    suspend fun getStopInfo(stopId: Long, lineId: Long? = null, direction: Direction? = null): StopInfo {
        return api.getStopInfo(stopId, lineId, direction)
    }

    suspend fun getStopDetails(stopId: Long): StopDetails {
        return api.getStopDetails(stopId)
    }

    suspend fun getVehicles(lineId: Long, direction: Direction): List<VehiclePosition> {
        return api.getVehicles(lineId, direction)
    }

    suspend fun getAnnouncements(limit: Int = 50, offset: Int = 0): List<Announcement> {
        return api.getAnnouncements(limit, offset)
    }

    suspend fun getHomeStops(stopId: Long): List<Stop> {
        return api.getHomeStops(stopId)
    }
}
