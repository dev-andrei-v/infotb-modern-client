package ro.infotb.modern

import android.content.Context
import android.os.Build
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

object AndroidContext {
    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual fun createSettings(): Settings {
    val prefs = AndroidContext.appContext.getSharedPreferences("stb_prefs", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}

actual fun getPlatformName(): String = "Android"

actual fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

actual fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

actual fun currentHourOfDay(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

actual fun currentMinuteOfHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getSystemLanguage(): String = java.util.Locale.getDefault().language

actual fun supportsDynamicIsland(): Boolean = false

actual fun supportsLiveNotification(): Boolean =
    Build.MANUFACTURER.equals("samsung", ignoreCase = true) || Build.VERSION.SDK_INT >= 36
