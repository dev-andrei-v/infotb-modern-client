package ro.infotb.modern.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SystemAppearance(isDark: Boolean) {
    LaunchedEffect(isDark) {
        val style = if (isDark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
        UIApplication.sharedApplication.setStatusBarStyle(style, animated = true)
    }
}
