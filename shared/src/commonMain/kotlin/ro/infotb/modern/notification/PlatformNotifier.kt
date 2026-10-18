package ro.infotb.modern.notification

interface PlatformNotifier {
    fun showNotification(event: NotificationEvent)

    /** A one-off alert that STB published a notice about a line the user follows. */
    fun showNoticeNotification(noticeId: Long, title: String, body: String) {}
    fun startLiveTracking(event: NotificationEvent) {}
    fun updateLiveTracking(event: NotificationEvent) {}
    fun stopLiveTracking() {}

    /**
     * Registers a callback invoked when the user asks to stop tracking from
     * outside the app UI (for example the Dynamic Island "Stop" button).
     * Platforms without such a control keep the default no-op.
     */
    fun setOnUserStopTracking(callback: () -> Unit) {}
}
