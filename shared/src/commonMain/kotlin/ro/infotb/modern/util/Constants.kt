package ro.infotb.modern.util

import ro.infotb.modern.data.model.TransportType

object Constants {
    const val API_BASE_URL = "https://info.stb.ro/api/web/v2-6"
    const val APP_KEY = "gcALgRyZHC,qFonZ=Jde"
    const val APP_SOURCE = "ro.radcom.smartcity.web"
    const val APP_VERSION = "1.0.0"

    const val REFETCH_ARRIVALS_MS = 10_000L
    const val REFETCH_VEHICLES_MS = 10_000L
    const val STALE_LINES_MS = 5 * 60_000L

    val TRANSPORT_COLORS: Map<TransportType, String> = mapOf(
        TransportType.BUS to "#1E88E5",
        TransportType.TRAM to "#E53935",
        TransportType.CABLE_CAR to "#43A047",
        TransportType.SUBWAY to "#F57C00"
    )

    // Settings keys (simple key-value via multiplatform-settings)
    const val SETTINGS_KEY_APP_ID = "stb-app-id"
    const val SETTINGS_KEY_USER_INFO = "stb-user-info"
    const val SETTINGS_KEY_THEME = "stb-theme"
    const val SETTINGS_KEY_LOCALE = "stb-locale"

    // Database
    const val DB_NAME = "stb.db"
}
