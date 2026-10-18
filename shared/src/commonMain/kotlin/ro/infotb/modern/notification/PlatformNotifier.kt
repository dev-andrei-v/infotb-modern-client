package ro.infotb.modern.notification

interface PlatformNotifier {
    fun showNotification(event: NotificationEvent)
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
