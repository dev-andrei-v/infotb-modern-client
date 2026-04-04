package ro.infotb.modern.notification

class IosNotifier : PlatformNotifier {
    override fun showNotification(event: NotificationEvent) {
        // TODO: implement iOS local notifications via UNUserNotificationCenter
        println("Notification: Line ${event.lineName} — ${event.minutes} min at ${event.stopName}")
    }
}
