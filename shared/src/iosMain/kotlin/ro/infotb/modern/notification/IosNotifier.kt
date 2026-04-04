package ro.infotb.modern.notification

import platform.Foundation.NSNotificationCenter
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.Foundation.NSUUID

class IosNotifier : PlatformNotifier {

    companion object {
        const val START_LIVE_TRACKING = "ro.infotb.modern.START_LIVE_TRACKING"
        const val UPDATE_LIVE_TRACKING = "ro.infotb.modern.UPDATE_LIVE_TRACKING"
        const val STOP_LIVE_TRACKING = "ro.infotb.modern.STOP_LIVE_TRACKING"
    }

    init {
        // Request notification permissions on init
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound
            ) { granted, error ->
                if (granted) {
                    println("[IosNotifier] Notification permission granted")
                } else {
                    println("[IosNotifier] Notification permission denied: ${error?.localizedDescription}")
                }
            }
    }

    override fun showNotification(event: NotificationEvent) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Line ${event.lineName}")
            setBody(
                if (event.minutes <= 0) {
                    "Arriving now at ${event.stopName}!"
                } else {
                    "${event.minutes} min until arrival at ${event.stopName}"
                }
            )
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = NSUUID().UUIDString(),
            content = content,
            trigger = null // Deliver immediately
        )

        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (error != null) {
                    println("[IosNotifier] Failed to deliver notification: ${error.localizedDescription}")
                }
            }
    }

    override fun startLiveTracking(event: NotificationEvent) {
        postLiveTrackingNotification(START_LIVE_TRACKING, event)
    }

    override fun updateLiveTracking(event: NotificationEvent) {
        postLiveTrackingNotification(UPDATE_LIVE_TRACKING, event)
    }

    override fun stopLiveTracking() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = STOP_LIVE_TRACKING,
            `object` = null
        )
    }

    private fun postLiveTrackingNotification(name: String, event: NotificationEvent) {
        val userInfo = mapOf<Any?, Any?>(
            "activityId" to event.activityId,
            "lineName" to event.lineName,
            "lineColor" to event.lineColor,
            "stopName" to event.stopName,
            "stopId" to event.stopId,
            "lineId" to event.lineId,
            "direction" to event.direction,
            "minutes" to event.minutes,
            "isTimetable" to event.isTimetable
        )
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = name,
            `object` = null,
            userInfo = userInfo
        )
    }
}
