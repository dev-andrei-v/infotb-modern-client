package ro.infotb.modern.data.store

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ro.infotb.modern.getSystemLanguage
import ro.infotb.modern.util.Constants

class PreferencesStore(private val settings: Settings) {

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
}
