package ro.infotb.modern

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual fun createSettings(): Settings {
    val prefs = Preferences.userRoot().node("ro/stb/client")
    return PreferencesSettings(prefs)
}

actual fun getPlatformName(): String = "Desktop"

actual fun getDeviceName(): String {
    val os = System.getProperty("os.name") ?: "Unknown"
    val arch = System.getProperty("os.arch") ?: "Unknown"
    return "$os ($arch)"
}

actual fun getOsVersion(): String {
    val name = System.getProperty("os.name") ?: "Unknown"
    val version = System.getProperty("os.version") ?: "Unknown"
    return "$name $version"
}

actual fun currentHourOfDay(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

actual fun currentMinuteOfHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getSystemLanguage(): String = java.util.Locale.getDefault().language

actual fun supportsDynamicIsland(): Boolean = false

actual fun supportsLiveNotification(): Boolean = false
