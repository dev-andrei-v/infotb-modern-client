package ro.infotb.modern.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.NotificationSubscription

private const val POLL_INTERVAL_MS = 30_000L

data class NotificationEvent(
    val lineName: String,
    val lineColor: String,
    val stopName: String,
    val stopId: Long,
    val lineId: Long,
    val direction: Int,
    val minutes: Int,
    val isTimetable: Boolean
)

class NotificationEngine(
    private val notificationStore: NotificationStore,
    private val repository: TransitRepository,
    private val scope: CoroutineScope,
    private val platformNotifier: PlatformNotifier
) {
    private var pollJob: Job? = null
    private val firedSet = mutableMapOf<String, Long>()
    private var liveTrackingActive = false

    // In-app notification events for toast display
    private val _events = MutableStateFlow<NotificationEvent?>(null)
    val events: StateFlow<NotificationEvent?> = _events.asStateFlow()

    fun clearEvent() {
        _events.value = null
    }

    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        if (liveTrackingActive) {
            platformNotifier.stopLiveTracking()
            liveTrackingActive = false
        }
    }

    private suspend fun poll() {
        val subs = notificationStore.subscriptions.value.filter { it.enabled }

        if (subs.isEmpty()) {
            if (liveTrackingActive) {
                platformNotifier.stopLiveTracking()
                liveTrackingActive = false
            }
            return
        }

        val now = currentTimeMillis()

        // Track the first active subscription for the live pill
        var pillEvent: NotificationEvent? = null

        for (sub in subs) {
            try {
                val info = repository.getStopInfo(sub.stopId, sub.lineId, sub.direction)
                val line = info.lines.find { l ->
                    l.id == sub.lineId && l.direction == sub.direction
                } ?: continue

                if (line.arrivingTime < 0) continue

                val minutes = (line.arrivingTime / 60).toInt()
                val event = NotificationEvent(
                    lineName = sub.lineName,
                    lineColor = sub.lineColor,
                    stopName = sub.stopName,
                    stopId = sub.stopId,
                    lineId = sub.lineId,
                    direction = sub.direction.value,
                    minutes = minutes,
                    isTimetable = line.isTimetable
                )

                // Always update the pill with the closest arrival
                if (pillEvent == null || minutes < pillEvent.minutes) {
                    pillEvent = event
                }

                // Bus arrived
                if (minutes <= 0) {
                    val key = "${sub.id}-arrived"
                    if (key !in firedSet) {
                        firedSet[key] = now
                        fireNotification(sub, 0, line.isTimetable)
                    }
                    notificationStore.toggleSubscription(sub.id)
                    continue
                }

                if (sub.mode == NotificationMode.ONCE) {
                    if (minutes <= sub.thresholdMinutes) {
                        val key = "${sub.id}-once"
                        if (key !in firedSet) {
                            firedSet[key] = now
                            fireNotification(sub, minutes, line.isTimetable)
                            notificationStore.toggleSubscription(sub.id)
                        }
                    }
                } else {
                    val key = "${sub.id}-repeat"
                    val lastFired = firedSet[key] ?: 0L
                    val cooldown = sub.thresholdMinutes * 60_000L

                    if (now - lastFired >= cooldown) {
                        firedSet[key] = now
                        fireNotification(sub, minutes, line.isTimetable)
                    }
                }
            } catch (_: Exception) {
                // Silently skip failed polls
            }
        }

        // Update the live pill
        if (pillEvent != null) {
            if (!liveTrackingActive) {
                platformNotifier.startLiveTracking(pillEvent)
                liveTrackingActive = true
            } else {
                platformNotifier.updateLiveTracking(pillEvent)
            }
        }
    }

    private fun fireNotification(sub: NotificationSubscription, minutes: Int, isTimetable: Boolean) {
        val event = NotificationEvent(
            lineName = sub.lineName,
            lineColor = sub.lineColor,
            stopName = sub.stopName,
            stopId = sub.stopId,
            lineId = sub.lineId,
            direction = sub.direction.value,
            minutes = minutes,
            isTimetable = isTimetable
        )
        _events.value = event
        platformNotifier.showNotification(event)
    }
}
