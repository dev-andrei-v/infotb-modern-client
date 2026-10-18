package ro.infotb.modern.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.api.StbApi
import ro.infotb.modern.data.model.*
import ro.infotb.modern.util.Constants

class TransitRepository(private val api: StbApi) {

    private data class CachedStopInfo(val info: StopInfo, val fetchedAtMs: Long)

    private val stopInfoCache = mutableMapOf<String, CachedStopInfo>()
    private val stopInfoInFlight = mutableMapOf<String, CompletableDeferred<StopInfo>>()
    private val stopInfoLock = Mutex()

    suspend fun getAllLines(): List<Line> {
        return api.getAllLines()
    }

    suspend fun getLineDetail(lineId: Long): LineDetail {
        return api.getLineDetail(lineId)
    }

    suspend fun getLineDirection(lineId: Long, direction: Direction): LineDetail {
        return api.getLineDirection(lineId, direction)
    }

    /**
     * Fetches arrivals for a stop.
     *
     * Results younger than [maxAgeMs] are served from memory, and concurrent
     * callers asking for the same stop share a single network request. The
     * dashboard, stop detail screen and notification engine all poll stops, so
     * this keeps them from hammering the API with duplicate calls.
     */
    suspend fun getStopInfo(
        stopId: Long,
        lineId: Long? = null,
        direction: Direction? = null,
        maxAgeMs: Long = Constants.STOP_INFO_CACHE_MS
    ): StopInfo {
        val key = "${stopId}_${lineId ?: ""}_${direction?.value ?: ""}"
        val now = currentTimeMillis()

        val pending: CompletableDeferred<StopInfo>
        val isOwner: Boolean
        stopInfoLock.withLock {
            stopInfoCache[key]?.let { cached ->
                if (now - cached.fetchedAtMs <= maxAgeMs) return cached.info
            }
            val existing = stopInfoInFlight[key]
            if (existing != null) {
                pending = existing
                isOwner = false
            } else {
                pending = CompletableDeferred()
                stopInfoInFlight[key] = pending
                isOwner = true
            }
        }

        if (!isOwner) return pending.await()

        try {
            val info = api.getStopInfo(stopId, lineId, direction)
            stopInfoLock.withLock {
                stopInfoCache[key] = CachedStopInfo(info, currentTimeMillis())
                stopInfoInFlight.remove(key)
            }
            pending.complete(info)
            return info
        } catch (e: Throwable) {
            stopInfoLock.withLock { stopInfoInFlight.remove(key) }
            pending.completeExceptionally(e)
            throw e
        }
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
