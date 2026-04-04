package ro.infotb.modern.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSNotificationCenter

private const val ThemeDidChangeNotification = "InfoTBThemeDidChange"

@Composable
actual fun SystemAppearance(isDark: Boolean) {
    LaunchedEffect(isDark) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = ThemeDidChangeNotification,
            `object` = null,
            userInfo = mapOf("isDark" to isDark)
        )
    }
}
