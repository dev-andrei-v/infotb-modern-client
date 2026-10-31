package ro.infotb.modern.notification

import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.supportsLiveNotification

/**
 * What happens when the user taps the bell next to an arrival. Shared by the
 * Compose and SwiftUI front ends so both start tracking the same way.
 */
class ArrivalTracker(
    private val notificationStore: NotificationStore,
    private val notificationEngine: NotificationEngine
) {
    /**
     * Starts following one arrival. Where the platform has a live surface
     * (Dynamic Island, ongoing notification) it is shown right away and this
     * becomes the only subscription; otherwise a one-off alert is added.
     *
     * @return true when live tracking started, so the caller can confirm it.
     */
    fun track(
        stopId: Long,
        stopName: String,
        lineId: Long,
        lineName: String,
        lineColor: String,
        direction: Direction,
        minutes: Int,
        isTimetable: Boolean,
        lineType: String
    ): Boolean {
        if (!supportsLiveNotification()) {
            notificationStore.addSubscription(
                stopId = stopId,
                stopName = stopName,
                lineId = lineId,
                lineName = lineName,
                lineColor = lineColor,
                direction = direction,
                mode = NotificationMode.ONCE,
                thresholdMinutes = DEFAULT_THRESHOLD_MINUTES
            )
            return false
        }

        notificationEngine.startLiveTrackingNow(
            NotificationEvent(
                lineName = lineName,
                lineColor = lineColor,
                stopName = stopName,
                stopId = stopId,
                lineId = lineId,
                direction = direction.value,
                minutes = minutes,
                isTimetable = isTimetable,
                lineType = lineType
            )
        )
        notificationStore.replaceWithSingleSubscription(
            stopId = stopId,
            stopName = stopName,
            lineId = lineId,
            lineName = lineName,
            lineColor = lineColor,
            direction = direction,
            mode = NotificationMode.ONCE,
            thresholdMinutes = DEFAULT_THRESHOLD_MINUTES,
            onComplete = { notificationEngine.refreshNow() }
        )
        return true
    }

    private companion object {
        const val DEFAULT_THRESHOLD_MINUTES = 3
    }
}
