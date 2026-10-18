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
        /** Posted by Swift when the user taps "Stop" in the island or the app is terminated. */
        private const val USER_STOP_TRACKING = "ro.infotb.modern.USER_STOP_TRACKING"
    }

    private var liveTrackingActive = false
    private var onUserStopTracking: (() -> Unit)? = null

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
            liveTrackingActive = false
            val info = notification?.userInfo ?: return@addObserverForName
            showLocalNotification(
                lineName = info["lineName"] as? String ?: "",
                stopName = info["stopName"] as? String ?: "",
                minutes = (info["minutes"] as? Number)?.toInt() ?: 0,
                isTimetable = (info["isTimetable"] as? Boolean) ?: false
            )
        }
    }

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = USER_STOP_TRACKING,
            `object` = null,
            queue = null
        ) { _ ->
            println("[IosNotifier] User requested stop tracking")
            liveTrackingActive = false
            onUserStopTracking?.invoke()
        }
    }

    override fun setOnUserStopTracking(callback: () -> Unit) {
        onUserStopTracking = callback
    }

    override fun showNotification(event: NotificationEvent) {
        // When Dynamic Island is active, it's already being updated by updateLiveTracking.
        // Don't send a duplicate update that could race and trigger a fallback notification.
        if (liveTrackingActive) return

        showLocalNotification(event.lineName, event.stopName, event.minutes, event.isTimetable)
    }

    override fun startLiveTracking(event: NotificationEvent) {
        if (supportsLiveActivities()) {
            postLiveTrackingNotification(START_LIVE_TRACKING, event)
            liveTrackingActive = true
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
        liveTrackingActive = false
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = STOP_LIVE_TRACKING,
            `object` = null
        )
    }

    override fun showNoticeNotification(noticeId: Long, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setThreadIdentifier("stb-notices")
            setSound(UNNotificationSound.defaultSound())
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "notice-$noticeId",
            content = content,
            trigger = null
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (error != null) {
                    println("[IosNotifier] Failed to deliver notice: ${error.localizedDescription}")
                }
            }
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
            "isTimetable" to event.isTimetable,
            "lineType" to event.lineType,
            "hasArrived" to event.hasArrived,
            // -1 means "unknown"; NSDictionary can't carry Kotlin nulls cleanly
            "stopsAway" to (event.stopsAway ?: -1),
            "distanceMeters" to (event.distanceMeters ?: -1),
            "progress" to (event.progress ?: -1.0),
            "nextMinutes" to event.nextMinutes
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
