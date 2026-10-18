package ro.infotb.modern.data.store

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ro.infotb.modern.getSystemLanguage
import ro.infotb.modern.util.Constants

/** How dashboard cards are arranged. */
enum class DashboardSort { GROUPS, NEAREST }

class PreferencesStore(private val settings: Settings) {

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
}
