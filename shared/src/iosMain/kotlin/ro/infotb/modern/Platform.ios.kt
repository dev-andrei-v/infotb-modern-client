package ro.infotb.modern

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

actual fun createSettings(): Settings {
    return NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
}

actual fun getPlatformName(): String = "iOS"

actual fun getDeviceName(): String {
    return UIDevice.currentDevice.name
}

actual fun getOsVersion(): String {
    return "iOS ${UIDevice.currentDevice.systemVersion}"
}

actual fun currentHourOfDay(): Int {
    val calendar = platform.Foundation.NSCalendar.currentCalendar
    val components = calendar.components(
        platform.Foundation.NSCalendarUnitHour,
        fromDate = platform.Foundation.NSDate()
    )
    return components.hour.toInt()
}

actual fun currentMinuteOfHour(): Int {
    val calendar = platform.Foundation.NSCalendar.currentCalendar
    val components = calendar.components(
        platform.Foundation.NSCalendarUnitMinute,
        fromDate = platform.Foundation.NSDate()
    )
    return components.minute.toInt()
}

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual fun getSystemLanguage(): String {
    val preferred = platform.Foundation.NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String
    return preferred?.take(2) ?: "en"
}
