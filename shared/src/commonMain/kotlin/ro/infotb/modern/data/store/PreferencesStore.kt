package ro.infotb.modern.data.store

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ro.infotb.modern.getSystemLanguage
import ro.infotb.modern.util.Constants

/** How dashboard cards are arranged. */
enum class DashboardSort { GROUPS, NEAREST }

class PreferencesStore(private val settings: Settings) {

    private val dismissedNewsKey = "announcements.dismissed.v1"
    private val _dismissedAnnouncementIds = MutableStateFlow(
        settings.getString(dismissedNewsKey, "").split(',').mapNotNull { it.toLongOrNull() }.toSet()
    )
    val dismissedAnnouncementIds: StateFlow<Set<Long>> = _dismissedAnnouncementIds.asStateFlow()

    fun dismissAnnouncement(id: Long) {
        val updated = _dismissedAnnouncementIds.value + id
        settings.putString(dismissedNewsKey, updated.joinToString(","))
        _dismissedAnnouncementIds.value = updated
    }

    private val _nearbyStopCount = MutableStateFlow(settings.getInt("lineNearbyStopCount", 3).coerceIn(0, 5))
    val nearbyStopCount: StateFlow<Int> = _nearbyStopCount.asStateFlow()
    private val _nearbyStopRadius = MutableStateFlow(settings.getInt("lineNearbyStopRadius", 1000))
    val nearbyStopRadius: StateFlow<Int> = _nearbyStopRadius.asStateFlow()

    fun setNearbyStopCount(count: Int) {
        val value = count.coerceIn(0, 5)
        settings.putInt("lineNearbyStopCount", value)
        _nearbyStopCount.value = value
    }

    fun setNearbyStopRadius(radius: Int) {
        val value = radius.takeIf { it in listOf(200, 500, 1000, 2000) } ?: 1000
        settings.putInt("lineNearbyStopRadius", value)
        _nearbyStopRadius.value = value
    }

    private val lineDisplayStyleKey = "lines.display_style"
    private val _lineDisplayStyle = MutableStateFlow(settings.getString(lineDisplayStyleKey, "normal"))
    val lineDisplayStyle: StateFlow<String> = _lineDisplayStyle.asStateFlow()

    fun setLineDisplayStyle(style: String) {
        val normalized = if (style == "bus_display") "bus_display" else "normal"
        settings.putString(lineDisplayStyleKey, normalized)
        _lineDisplayStyle.value = normalized
    }

    private val homeWidgetStopKey = "dashboard.home_widget_stop_id"
    private val _homeWidgetStopId = MutableStateFlow(settings.getStringOrNull(homeWidgetStopKey)?.toLongOrNull())
    val homeWidgetStopId: StateFlow<Long?> = _homeWidgetStopId.asStateFlow()

    fun setHomeWidgetStopId(stopId: Long?) {
        if (stopId == null) settings.remove(homeWidgetStopKey) else settings.putString(homeWidgetStopKey, stopId.toString())
        _homeWidgetStopId.value = stopId
    }

    private val homeWidgetStopsKey = "dashboard.home_widget_stop_ids.v1"
    private val widgetStopsJson = Json { ignoreUnknownKeys = true }
    private val _homeWidgetStopIds = MutableStateFlow(
        settings.getStringOrNull(homeWidgetStopsKey)?.let {
            runCatching { widgetStopsJson.decodeFromString<Map<String, Long>>(it) }.getOrNull()
        }.orEmpty()
    )
    val homeWidgetStopIds: StateFlow<Map<String, Long>> = _homeWidgetStopIds.asStateFlow()

    fun setHomeWidgetStop(groupName: String, stopId: Long?) {
        val updated = _homeWidgetStopIds.value.toMutableMap()
        if (stopId == null) updated.remove(groupName) else updated[groupName] = stopId
        settings.putString(homeWidgetStopsKey, widgetStopsJson.encodeToString(updated))
        _homeWidgetStopIds.value = updated
    }

    private val _dashboardSort = MutableStateFlow(
        settings.getStringOrNull(Constants.SETTINGS_KEY_DASHBOARD_SORT)
            ?.let { v -> DashboardSort.entries.firstOrNull { it.name == v } } ?: DashboardSort.GROUPS
    )
    val dashboardSort: StateFlow<DashboardSort> = _dashboardSort.asStateFlow()

    fun setDashboardSort(sort: DashboardSort) {
        settings.putString(Constants.SETTINGS_KEY_DASHBOARD_SORT, sort.name)
        _dashboardSort.value = sort
    }

    private val _theme = MutableStateFlow(settings.getString(Constants.SETTINGS_KEY_THEME, "system"))
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _locale = MutableStateFlow(
        settings.getString(Constants.SETTINGS_KEY_LOCALE, if (getSystemLanguage() == "ro") "ro" else "en")
    )
    val locale: StateFlow<String> = _locale.asStateFlow()

    fun setTheme(theme: String) {
        settings.putString(Constants.SETTINGS_KEY_THEME, theme)
        _theme.value = theme
    }

    fun setLocale(locale: String) {
        settings.putString(Constants.SETTINGS_KEY_LOCALE, locale)
        _locale.value = locale
    }

    /** Whether new STB notices about followed lines raise a system notification. */
    private val _noticeAlerts = MutableStateFlow(settings.getBoolean(Constants.SETTINGS_KEY_NOTICE_ALERTS, true))
    val noticeAlerts: StateFlow<Boolean> = _noticeAlerts.asStateFlow()

    fun setNoticeAlerts(enabled: Boolean) {
        settings.putBoolean(Constants.SETTINGS_KEY_NOTICE_ALERTS, enabled)
        _noticeAlerts.value = enabled
    }

    fun getSavedTrips(): String? = settings.getStringOrNull("planner.saved_trips.json")
    fun setSavedTrips(json: String) = settings.putString("planner.saved_trips.json", json)
}
