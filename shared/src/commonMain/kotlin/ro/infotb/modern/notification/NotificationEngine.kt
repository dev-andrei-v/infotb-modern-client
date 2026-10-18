package ro.infotb.modern.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.util.computeVehicleProximity
import ro.infotb.modern.util.decodePolyline
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.NotificationSubscription

private const val POLL_INTERVAL_MS = 30_000L

/** How long the island keeps showing "arrived" before tracking ends on its own. */
private const val ARRIVAL_GRACE_MS = 2 * 60_000L

/** Hard cap on one tracking session so a forgotten subscription can't run all day. */
private const val MAX_TRACKING_SESSION_MS = 90 * 60_000L

/** If the countdown was this low and then jumps up, the vehicle passed the stop between polls. */
private const val PASSED_STOP_THRESHOLD_MIN = 2
private const val PASSED_STOP_JUMP_MIN = 5

data class NotificationEvent(
    val lineName: String,
    val lineColor: String,
    val stopName: String,
    val stopId: Long,
    val lineId: Long,
    val direction: Int,
    val minutes: Int,
    val isTimetable: Boolean,
    /** Vehicle type name (BUS, TRAM, CABLE_CAR = trolleybus, SUBWAY) so the island can draw the right glyph. */
    val lineType: String = "BUS",
    /** True once the tracked vehicle has reached (or passed) the stop. */
    val hasArrived: Boolean = false,
    /** Stops the approaching vehicle still has to reach (1 = yours is next), when known. */
    val stopsAway: Int? = null,
    /** Distance along the route from the vehicle to the stop, when known. */
    val distanceMeters: Int? = null,
    /** 0..1 position of the vehicle inside the last few stops before yours, when known. */
    val progress: Double? = null,
    /** Minutes until the arrivals after this one. */
    val nextMinutes: List<Int> = emptyList()
) {
    val activityId: String
        get() = "$stopId-$lineId-$direction"
}

class NotificationEngine(
    private val notificationStore: NotificationStore,
    private val repository: TransitRepository,
    private val scope: CoroutineScope,
    private val platformNotifier: PlatformNotifier,
    private val noticeWatcher: NoticeWatcher? = null
) {
    private var pollJob: Job? = null
    private val firedSet = mutableMapOf<String, Long>()
    private var liveTrackingActive = false
    private var trackingStartedMs = 0L
    /** subscription id -> wall-clock time its vehicle was first seen arriving */
    private val arrivedAt = mutableMapOf<String, Long>()
    /** subscription id -> minutes reported by the previous poll */
    private val lastMinutes = mutableMapOf<String, Int>()

    /** Route shape and ordered stops per "lineId_direction", used to place vehicles. */
    private data class RouteInfo(val polyline: List<Pair<Double, Double>>, val stops: List<Stop>)
    private val routeCache = mutableMapOf<String, RouteInfo>()
    private val pollMutex = Mutex()

    init {
        platformNotifier.setOnUserStopTracking { stopTrackingByUser() }
    }

    // In-app notification events for toast display
    private val _events = MutableStateFlow<NotificationEvent?>(null)
    val events: StateFlow<NotificationEvent?> = _events.asStateFlow()

    fun clearEvent() {
        _events.value = null
    }

    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            // React to subscription changes immediately
            launch {
                notificationStore.subscriptions.collect { subs ->
                    val enabled = subs.filter { it.enabled }
                    if (enabled.isEmpty() && liveTrackingActive) {
                        // Debounce: replaceWithSingleSubscription emits empty
                        // list briefly between deleteAll and upsert
                        delay(500)
                        val current = notificationStore.subscriptions.value.filter { it.enabled }
                        if (current.isEmpty()) {
                            pollMutex.withLock { endLiveTracking() }
                        }
                    } else if (enabled.isNotEmpty()) {
                        poll()
                    }
                }
            }
            // Regular polling loop
            while (true) {
                poll()
                // Piggyback the notice check on the arrival polling, at most every 15 min
                noticeWatcher?.checkIfDue()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        // Do NOT stop live tracking here — keep the dynamic island / ongoing
        // notification persistent across app lifecycle events. Live tracking
        // is only stopped when all subscriptions are disabled or removed.
    }

    fun refreshNow() {
        scope.launch { poll() }
    }

    fun startLiveTrackingNow(event: NotificationEvent) {
        // Don't call stopLiveTracking() here — LiveActivityManager.startTracking
        // already ends the old activity before creating a new one. Calling stop
        // separately races with start and can end the *new* activity.
        platformNotifier.startLiveTracking(event)
        liveTrackingActive = true
        trackingStartedMs = currentTimeMillis()
        resetSessionState()
    }

    /**
     * Ends the current tracking session: disables every subscription and takes
     * the live pill down. Used by the island/notification "Stop" action and
     * when a session ends on its own (arrival or time cap).
     */
    fun stopTrackingByUser() {
        scope.launch {
            pollMutex.withLock { endLiveTracking() }
            notificationStore.disableAll()
        }
    }

    private fun endLiveTracking() {
        if (liveTrackingActive) {
            platformNotifier.stopLiveTracking()
            liveTrackingActive = false
        }
        trackingStartedMs = 0L
        resetSessionState()
    }

    private fun resetSessionState() {
        firedSet.clear()
        arrivedAt.clear()
        lastMinutes.clear()
    }

    private suspend fun poll() = pollMutex.withLock {
        val subs = notificationStore.subscriptions.value.filter { it.enabled }

        if (subs.isEmpty()) {
            endLiveTracking()
            return
        }

        val now = currentTimeMillis()

        // A session that has run for too long ends on its own.
        if (liveTrackingActive && trackingStartedMs > 0 && now - trackingStartedMs > MAX_TRACKING_SESSION_MS) {
            endLiveTracking()
            notificationStore.disableAll()
            return
        }

        // Track the closest active subscription for the live pill
        var pillEvent: NotificationEvent? = null
        var graceExpired = false

        for (sub in subs) {
            try {
                val info = repository.getStopInfo(sub.stopId, sub.lineId, sub.direction)
                val line = info.lines.find { l ->
                    l.id == sub.lineId && l.direction == sub.direction
                } ?: continue

                if (line.arrivingTime < 0) continue

                val reportedMinutes = (line.arrivingTime / 60).toInt()
                val previous = lastMinutes[sub.id]
                lastMinutes[sub.id] = reportedMinutes

                // Arrival is either a zero countdown, or a countdown that was
                // nearly zero and suddenly jumped up (the API moved on to the
                // next vehicle because this one already passed the stop).
                val justArrived = reportedMinutes <= 0 ||
                    (previous != null && previous <= PASSED_STOP_THRESHOLD_MIN &&
                        reportedMinutes >= previous + PASSED_STOP_JUMP_MIN)
                if (justArrived && sub.id !in arrivedAt) {
                    arrivedAt[sub.id] = now
                }
                val arrivedSince = arrivedAt[sub.id]
                val hasArrived = arrivedSince != null
                val minutes = if (hasArrived) 0 else reportedMinutes

                val proximity = if (hasArrived || line.isTimetable) null else {
                    locateVehicle(sub.lineId, sub.direction, sub.stopId, line.segmentPath, line.vehicles)
                }

                val event = NotificationEvent(
                    lineName = sub.lineName,
                    lineColor = sub.lineColor,
                    stopName = sub.stopName,
                    stopId = sub.stopId,
                    lineId = sub.lineId,
                    direction = sub.direction.value,
                    minutes = minutes,
                    isTimetable = line.isTimetable,
                    lineType = line.type.name,
                    hasArrived = hasArrived,
                    stopsAway = proximity?.stopsAway,
                    distanceMeters = proximity?.distanceMeters,
                    progress = proximity?.progress,
                    nextMinutes = line.arrivingTimes.drop(1).take(2)
                        .map { (it.arrivingTime / 60).toInt().coerceAtLeast(0) }
                )

                // Always update the pill with the closest arrival
                if (pillEvent == null || minutes < pillEvent.minutes) {
                    pillEvent = event
                }

                if (hasArrived) {
                    val key = "${sub.id}-arrived"
                    if (key !in firedSet) {
                        firedSet[key] = now
                        fireNotification(sub, 0, line.isTimetable, line.type.name)
                    }
                    if (now - arrivedSince!! >= ARRIVAL_GRACE_MS) {
                        graceExpired = true
                    }
                    continue
                }

                if (sub.mode == NotificationMode.ONCE) {
                    if (minutes <= sub.thresholdMinutes) {
                        val key = "${sub.id}-once"
                        if (key !in firedSet) {
                            firedSet[key] = now
                            fireNotification(sub, minutes, line.isTimetable, line.type.name)
                            // Do NOT toggle/disable — keep subscription active
                            // so live tracking (dynamic island) stays persistent
                        }
                    }
                } else {
                    val key = "${sub.id}-repeat"
                    val lastFired = firedSet[key] ?: 0L
                    val cooldown = sub.thresholdMinutes * 60_000L

                    if (now - lastFired >= cooldown) {
                        firedSet[key] = now
                        fireNotification(sub, minutes, line.isTimetable, line.type.name)
                    }
                }
            } catch (_: Exception) {
                // Silently skip failed polls
            }
        }

        // The vehicle arrived a while ago: the session is over.
        if (graceExpired) {
            endLiveTracking()
            notificationStore.disableAll()
            return
        }

        // Update the live pill / dynamic island
        if (pillEvent != null) {
            if (!liveTrackingActive) {
                platformNotifier.startLiveTracking(pillEvent)
                liveTrackingActive = true
                trackingStartedMs = now
            } else {
                platformNotifier.updateLiveTracking(pillEvent)
            }
        } else if (liveTrackingActive) {
            endLiveTracking()
        }
    }

    /**
     * Works out where the next approaching vehicle is relative to the stop.
     * The route shape and stop list are fetched once per line/direction and
     * cached for the life of the engine; vehicles come with the stop info,
     * with a direct vehicles call as fallback. Any failure just means no
     * proximity for this poll.
     */
    private suspend fun locateVehicle(
        lineId: Long,
        direction: Direction,
        stopId: Long,
        segmentPath: String?,
        vehiclesFromStop: List<VehiclePosition>
    ) = try {
        val key = "${lineId}_${direction.value}"
        val route = routeCache[key] ?: run {
            val detail = repository.getLineDirection(lineId, direction)
            val polyline = detail.segmentPath?.let { decodePolyline(it) } ?: emptyList()
            RouteInfo(polyline, detail.stops).also { routeCache[key] = it }
        }
        val polyline = segmentPath?.let { decodePolyline(it) }?.takeIf { it.size >= 2 } ?: route.polyline
        val vehicles = vehiclesFromStop.ifEmpty { repository.getVehicles(lineId, direction) }
        computeVehicleProximity(polyline, route.stops, stopId, vehicles)
    } catch (_: Exception) {
        null
    }

    private fun fireNotification(sub: NotificationSubscription, minutes: Int, isTimetable: Boolean, lineType: String) {
        val event = NotificationEvent(
            lineName = sub.lineName,
            lineColor = sub.lineColor,
            stopName = sub.stopName,
            stopId = sub.stopId,
            lineId = sub.lineId,
            direction = sub.direction.value,
            minutes = minutes,
            isTimetable = isTimetable,
            lineType = lineType
        )
        // Skip all notifications when live tracking is active — the dynamic island
        // or ongoing notification already shows the info.
        if (!liveTrackingActive) {
            _events.value = event
            platformNotifier.showNotification(event)
        }
    }
}
