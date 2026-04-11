package ro.infotb.modern

import com.russhwolf.settings.Settings

expect fun createSettings(): Settings

expect fun getPlatformName(): String

expect fun getDeviceName(): String

expect fun getOsVersion(): String

expect fun currentHourOfDay(): Int

expect fun currentMinuteOfHour(): Int

expect fun currentTimeMillis(): Long

expect fun getSystemLanguage(): String

expect fun supportsDynamicIsland(): Boolean
