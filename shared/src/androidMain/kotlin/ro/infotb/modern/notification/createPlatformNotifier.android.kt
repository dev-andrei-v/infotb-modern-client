package ro.infotb.modern.notification

actual fun createPlatformNotifier(): PlatformNotifier = AndroidNotifier()
