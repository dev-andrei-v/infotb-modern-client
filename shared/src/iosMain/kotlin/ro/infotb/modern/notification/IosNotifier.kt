package ro.infotb.modern.notification

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

class IosNotifier : PlatformNotifier {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    override fun showNotification(event: NotificationEvent) {
        val title = if (event.minutes <= 0) {
            "Line ${event.lineName} — Arrived!"
        } else {
            "Line ${event.lineName} — ${event.minutes} min"
        }
        val source = if (event.isTimetable) "scheduled" else "GPS"
        val body = "Arrives at ${event.stopName} ($source)"

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
        }

        val requestId = "alert-${event.stopId}-${event.lineName}"
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(requestId, content, trigger)

        center.addNotificationRequest(request) { _ -> }
    }

    override fun startLiveTracking(event: NotificationEvent) {
        updateLiveTracking(event)
    }

    override fun updateLiveTracking(event: NotificationEvent) {
        val minuteText = when {
            event.minutes <= 0 -> "NOW"
            event.minutes == 1 -> "1 min"
            else -> "${event.minutes} min"
        }
        val source = if (event.isTimetable) "Scheduled" else "GPS"

        val content = UNMutableNotificationContent().apply {
            setTitle("Line ${event.lineName} → ${event.stopName}")
            setBody("$minuteText  •  $source")
        }

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier("live-tracking", content, trigger)

        center.addNotificationRequest(request) { _ -> }
    }

    override fun stopLiveTracking() {
        center.removeDeliveredNotificationsWithIdentifiers(listOf("live-tracking"))
    }
}
