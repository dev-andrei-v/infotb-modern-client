package ro.infotb.modern.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

class IosNotifier : PlatformNotifier {

    companion object {
        const val START_LIVE_TRACKING = "ro.infotb.modern.START_LIVE_TRACKING"
        const val UPDATE_LIVE_TRACKING = "ro.infotb.modern.UPDATE_LIVE_TRACKING"
        const val STOP_LIVE_TRACKING = "ro.infotb.modern.STOP_LIVE_TRACKING"
        private const val LIVE_ACTIVITY_FAILED = "ro.infotb.modern.LIVE_ACTIVITY_FAILED"
    }

    init {
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

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = LIVE_ACTIVITY_FAILED,
            `object` = null,
            queue = null
        ) { notification: NSNotification? ->
            println("[IosNotifier] Live Activity failed, falling back to local notification")
            val info = notification?.userInfo ?: return@addObserverForName
            showLocalNotification(
                lineName = info["lineName"] as? String ?: "",
                stopName = info["stopName"] as? String ?: "",
                minutes = (info["minutes"] as? Number)?.toInt() ?: 0,
                isTimetable = (info["isTimetable"] as? Boolean) ?: false
            )
        }
    }

    override fun showNotification(event: NotificationEvent) {
        if (supportsLiveActivities()) {
            postLiveTrackingNotification(UPDATE_LIVE_TRACKING, event)
            return
        }
        showLocalNotification(event.lineName, event.stopName, event.minutes, event.isTimetable)
    }

    override fun startLiveTracking(event: NotificationEvent) {
        if (supportsLiveActivities()) {
            postLiveTrackingNotification(START_LIVE_TRACKING, event)
        } else {
            showLocalNotification(event.lineName, event.stopName, event.minutes, event.isTimetable)
        }
    }

    override fun updateLiveTracking(event: NotificationEvent) {
        if (supportsLiveActivities()) {
            postLiveTrackingNotification(UPDATE_LIVE_TRACKING, event)
        } else {
            showLocalNotification(event.lineName, event.stopName, event.minutes, event.isTimetable)
        }
    }

    override fun stopLiveTracking() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = STOP_LIVE_TRACKING,
            `object` = null
        )
    }

    private fun showLocalNotification(lineName: String, stopName: String, minutes: Int, isTimetable: Boolean) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Line $lineName")
            setBody(
                if (minutes <= 0) {
                    "Arriving now at $stopName!"
                } else {
                    "$minutes min until arrival at $stopName"
                }
            )
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = NSUUID().UUIDString(),
            content = content,
            trigger = null
        )

        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (error != null) {
                    println("[IosNotifier] Failed to deliver notification: ${error.localizedDescription}")
                }
            }
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

    @OptIn(ExperimentalForeignApi::class)
    private fun supportsLiveActivities(): Boolean {
        val minimumVersion = cValue<NSOperatingSystemVersion> {
            majorVersion = 16
            minorVersion = 2
            patchVersion = 0
        }
        return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(minimumVersion)
    }
}
