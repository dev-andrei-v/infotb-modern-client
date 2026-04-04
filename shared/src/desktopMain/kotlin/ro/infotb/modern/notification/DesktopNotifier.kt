package ro.infotb.modern.notification

class DesktopNotifier : PlatformNotifier {
    override fun showNotification(event: NotificationEvent) {
        println("Notification: Line ${event.lineName} — ${event.minutes} min at ${event.stopName}")
    }
}
