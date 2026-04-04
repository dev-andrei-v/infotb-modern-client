package ro.infotb.modern.notification

interface PlatformNotifier {
    fun showNotification(event: NotificationEvent)
    fun startLiveTracking(event: NotificationEvent) {}
    fun updateLiveTracking(event: NotificationEvent) {}
    fun stopLiveTracking() {}
}
