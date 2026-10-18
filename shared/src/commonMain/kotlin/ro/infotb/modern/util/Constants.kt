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

    /** Hard cap for a single HTTP request so one hung call can't stall a card. */
    const val REQUEST_TIMEOUT_MS = 10_000L

    /** Stop info younger than this is served from the in-memory cache. */
    const val STOP_INFO_CACHE_MS = 5_000L

    /** Max simultaneous stop-info requests from the dashboard. */
    const val DASHBOARD_MAX_PARALLEL_FETCHES = 4

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
    const val SETTINGS_KEY_DASHBOARD_SORT = "stb-dashboard-sort"
    const val SETTINGS_KEY_NOTICE_ALERTS = "stb-notice-alerts"
    const val SETTINGS_KEY_NOTICES_SEEN = "stb-notices-seen"

    // Database
    const val DB_NAME = "stb.db"
}
