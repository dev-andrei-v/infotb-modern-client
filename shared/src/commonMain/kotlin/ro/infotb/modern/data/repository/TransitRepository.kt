package ro.infotb.modern.data.repository

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.api.StbApi
import ro.infotb.modern.data.model.*
import ro.infotb.modern.util.Constants

class TransitRepository(private val api: StbApi, private val settings: Settings) {

    private val announcementsJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CachedLineCatalog(val fetchedAtMs: Long, val lines: List<Line>)

    @Serializable
    private data class CachedLineDetail(val fetchedAtMs: Long, val detail: LineDetail)

    private val lineDetailCacheJson = Json { ignoreUnknownKeys = true }
    private val lineDetailInFlight = mutableMapOf<String, CompletableDeferred<LineDetail>>()
    private val lineDetailLock = Mutex()

    private data class CachedStopInfo(val info: StopInfo, val fetchedAtMs: Long)

    @Serializable
    private data class CachedStops(val fetchedAtMs: Long, val stops: List<Stop>)

    @Serializable
    private data class CachedStopLines(val fetchedAtMs: Long, val lines: List<Line>)

    private val stopInfoCache = mutableMapOf<String, CachedStopInfo>()
    private val stopInfoInFlight = mutableMapOf<String, CompletableDeferred<StopInfo>>()
    private val stopInfoLock = Mutex()
    private val stopTileLock = Mutex()
    private val stopTileInFlight = mutableMapOf<String, CompletableDeferred<List<Stop>>>()
    private val stopLinesLock = Mutex()
    private val stopLinesInFlight = mutableMapOf<Long, CompletableDeferred<List<Line>>>()

    suspend fun getAllLines(): List<Line> {
        val cached = settings.getStringOrNull(Constants.SETTINGS_KEY_LINES_CACHE)
            ?.let { runCatching { lineDetailCacheJson.decodeFromString<CachedLineCatalog>(it) }.getOrNull() }
        if (cached != null && currentTimeMillis() - cached.fetchedAtMs <= Constants.LINE_DETAIL_CACHE_MS) {
            return cached.lines
        }
        return try {
            api.getAllLines().also { lines ->
                settings.putString(
                    Constants.SETTINGS_KEY_LINES_CACHE,
                    lineDetailCacheJson.encodeToString(CachedLineCatalog(currentTimeMillis(), lines))
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.lines ?: throw e
        }
    }

    fun getLinesCachedAtMs(): Long = settings.getStringOrNull(Constants.SETTINGS_KEY_LINES_CACHE)
        ?.let { runCatching { lineDetailCacheJson.decodeFromString<CachedLineCatalog>(it).fetchedAtMs }.getOrNull() }
        ?: 0L

    suspend fun refreshLinesAndStops(): List<Line> {
        val lines = api.getAllLines()
        settings.putString(
            Constants.SETTINGS_KEY_LINES_CACHE,
            lineDetailCacheJson.encodeToString(CachedLineCatalog(currentTimeMillis(), lines))
        )
        clearIndexedCache("${Constants.SETTINGS_KEY_LINE_DETAIL_CACHE}-index", Constants.SETTINGS_KEY_LINE_DETAIL_CACHE)
        clearIndexedCache("${Constants.SETTINGS_KEY_STOP_LINES_CACHE}-index", Constants.SETTINGS_KEY_STOP_LINES_CACHE)
        clearIndexedCache("${Constants.SETTINGS_KEY_STOP_TILE_CACHE}-index", Constants.SETTINGS_KEY_STOP_TILE_CACHE)
        return lines
    }

    suspend fun getLineDetail(lineId: Long): LineDetail {
        return getCachedLineDetail("$lineId-all") { api.getLineDetail(lineId) }
    }

    suspend fun getLineDirection(lineId: Long, direction: Direction): LineDetail {
        return getCachedLineDetail("$lineId-${direction.value}") { api.getLineDirection(lineId, direction) }
    }

    private suspend fun getCachedLineDetail(key: String, fetch: suspend () -> LineDetail): LineDetail {
        val settingKey = "${Constants.SETTINGS_KEY_LINE_DETAIL_CACHE}-$key"
        val cached = settings.getStringOrNull(settingKey)
            ?.let { runCatching { lineDetailCacheJson.decodeFromString<CachedLineDetail>(it) }.getOrNull() }
        val now = currentTimeMillis()
        if (cached != null && now - cached.fetchedAtMs <= Constants.LINE_DETAIL_CACHE_MS) return cached.detail

        val pending: CompletableDeferred<LineDetail>
        val isOwner: Boolean
        lineDetailLock.withLock {
            val existing = lineDetailInFlight[key]
            if (existing != null) {
                pending = existing
                isOwner = false
            } else {
                pending = CompletableDeferred()
                lineDetailInFlight[key] = pending
                isOwner = true
            }
        }
        if (!isOwner) return pending.await()

        try {
            val detail = fetch()
            settings.putString(settingKey, lineDetailCacheJson.encodeToString(CachedLineDetail(currentTimeMillis(), detail)))
            retainRecentLineDetails(key)
            lineDetailLock.withLock { lineDetailInFlight.remove(key) }
            pending.complete(detail)
            return detail
        } catch (e: CancellationException) {
            lineDetailLock.withLock { lineDetailInFlight.remove(key) }
            pending.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            lineDetailLock.withLock { lineDetailInFlight.remove(key) }
            if (cached != null) {
                pending.complete(cached.detail)
                return cached.detail
            }
            pending.completeExceptionally(e)
            throw e
        }
    }

    private fun retainRecentLineDetails(key: String) {
        val indexKey = "${Constants.SETTINGS_KEY_LINE_DETAIL_CACHE}-index"
        val previous = settings.getStringOrNull(indexKey)?.split('|')?.filter(String::isNotBlank).orEmpty()
        val retained = (previous.filterNot { it == key } + key).takeLast(Constants.MAX_CACHED_LINE_DIRECTIONS)
        (previous - retained.toSet()).forEach { settings.remove("${Constants.SETTINGS_KEY_LINE_DETAIL_CACHE}-$it") }
        settings.putString(indexKey, retained.joinToString("|"))
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

    suspend fun getStopLines(stopId: Long): List<Line> {
        val key = "${Constants.SETTINGS_KEY_STOP_LINES_CACHE}-$stopId"
        val cached = settings.getStringOrNull(key)
            ?.let { runCatching { lineDetailCacheJson.decodeFromString<CachedStopLines>(it) }.getOrNull() }
        if (cached != null && currentTimeMillis() - cached.fetchedAtMs <= Constants.STOP_LINES_CACHE_MS) return cached.lines

        val pending: CompletableDeferred<List<Line>>
        val isOwner: Boolean
        stopLinesLock.withLock {
            val existing = stopLinesInFlight[stopId]
            if (existing != null) {
                pending = existing
                isOwner = false
            } else {
                pending = CompletableDeferred()
                stopLinesInFlight[stopId] = pending
                isOwner = true
            }
        }
        if (!isOwner) return pending.await()
        try {
            val lines = api.getStopLines(stopId)
            settings.putString(key, lineDetailCacheJson.encodeToString(CachedStopLines(currentTimeMillis(), lines)))
            retainRecentEntries("${Constants.SETTINGS_KEY_STOP_LINES_CACHE}-index", stopId.toString(), Constants.MAX_CACHED_STOP_LINES)
            stopLinesLock.withLock { stopLinesInFlight.remove(stopId) }
            pending.complete(lines)
            return lines
        } catch (e: CancellationException) {
            stopLinesLock.withLock { stopLinesInFlight.remove(stopId) }
            pending.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            stopLinesLock.withLock { stopLinesInFlight.remove(stopId) }
            if (cached != null) { pending.complete(cached.lines); return cached.lines }
            pending.completeExceptionally(e)
            throw e
        }
    }

    suspend fun getVehicles(lineId: Long, direction: Direction): List<VehiclePosition> {
        return api.getVehicles(lineId, direction)
    }

    suspend fun getAnnouncements(limit: Int = 50, offset: Int = 0): List<Announcement> {
        return try {
            api.getAnnouncements(limit, offset).also { announcements ->
                settings.putString(announcementsCacheKey(limit, offset), announcementsJson.encodeToString(announcements))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            getCachedAnnouncements(limit, offset)?.let { return it }
            throw e
        }
    }

    suspend fun searchPlannerPlaces(query: String): List<PlannerPlace> = api.searchPlannerPlaces(query)

    suspend fun planRoutes(
        start: PlannerPlace,
        destination: PlannerPlace,
        maxWalkDistance: Int,
        startTimeMs: Long,
        vehicleTypes: List<String>,
        accessible: Boolean,
        language: String
    ): String = api.planRoutes(start, destination, maxWalkDistance, startTimeMs, vehicleTypes, accessible, language)

    fun getCachedAnnouncements(limit: Int = 50, offset: Int = 0): List<Announcement>? {
        val cached = settings.getStringOrNull(announcementsCacheKey(limit, offset)) ?: return null
        return runCatching { announcementsJson.decodeFromString<List<Announcement>>(cached) }.getOrNull()
    }

    private fun announcementsCacheKey(limit: Int, offset: Int) =
        "${Constants.SETTINGS_KEY_ANNOUNCEMENTS_CACHE}-$limit-$offset"

    suspend fun getHomeStops(stopId: Long): List<Stop> {
        return api.getHomeStops(stopId)
    }

    suspend fun getStopsInBounds(south: Double, west: Double, north: Double, east: Double): List<Stop> {
        // Request and cache 0.02° tiles so small pans reuse the same stop-location data.
        val step = 0.02
        val tileSouth = floor(south / step) * step
        val tileWest = floor(west / step) * step
        val tileNorth = ceil(north / step) * step
        val tileEast = ceil(east / step) * step
        val coordinates = listOf(tileSouth, tileWest, tileNorth, tileEast).map { (it * 100).toInt() }
        val tileId = coordinates.joinToString("_")
        val key = "${Constants.SETTINGS_KEY_STOP_TILE_CACHE}-$tileId"
        val cached = settings.getStringOrNull(key)
            ?.let { runCatching { lineDetailCacheJson.decodeFromString<CachedStops>(it) }.getOrNull() }
        val inTile = { stops: List<Stop> -> stops.filter { it.lat in south..north && it.lng in west..east } }
        if (cached != null && currentTimeMillis() - cached.fetchedAtMs <= Constants.STOP_LOCATION_CACHE_MS) return inTile(cached.stops)

        val pending: CompletableDeferred<List<Stop>>
        val isOwner: Boolean
        stopTileLock.withLock {
            val existing = stopTileInFlight[tileId]
            if (existing != null) {
                pending = existing
                isOwner = false
            } else {
                pending = CompletableDeferred()
                stopTileInFlight[tileId] = pending
                isOwner = true
            }
        }
        if (!isOwner) return inTile(pending.await())
        try {
            val stops = api.getStopsInBounds(tileSouth, tileWest, tileNorth, tileEast)
            settings.putString(key, lineDetailCacheJson.encodeToString(CachedStops(currentTimeMillis(), stops)))
            retainRecentEntries("${Constants.SETTINGS_KEY_STOP_TILE_CACHE}-index", tileId, Constants.MAX_CACHED_STOP_TILES)
            stopTileLock.withLock { stopTileInFlight.remove(tileId) }
            pending.complete(stops)
            return inTile(stops)
        } catch (e: CancellationException) {
            stopTileLock.withLock { stopTileInFlight.remove(tileId) }
            pending.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            stopTileLock.withLock { stopTileInFlight.remove(tileId) }
            if (cached != null) { pending.complete(cached.stops); return inTile(cached.stops) }
            pending.completeExceptionally(e)
            throw e
        }
    }

    private fun retainRecentEntries(indexKey: String, entry: String, maxEntries: Int) {
        val previous = settings.getStringOrNull(indexKey)?.split('|')?.filter(String::isNotBlank).orEmpty()
        val retained = (previous.filterNot { it == entry } + entry).takeLast(maxEntries)
        (previous - retained.toSet()).forEach { settings.remove("${indexKey.removeSuffix("-index")}-${it}") }
        settings.putString(indexKey, retained.joinToString("|"))
    }

    private fun clearIndexedCache(indexKey: String, keyPrefix: String) {
        settings.getStringOrNull(indexKey)?.split('|')?.filter(String::isNotBlank).orEmpty()
            .forEach { settings.remove("$keyPrefix-$it") }
        settings.remove(indexKey)
    }
}
