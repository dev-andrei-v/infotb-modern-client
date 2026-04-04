package ro.infotb.modern.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SystemAppearance(isDark: Boolean) {
    SideEffect {
        val interfaceStyle = if (isDark) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        UIApplication.sharedApplication.keyWindow?.rootViewController?.let { rootViewController ->
            rootViewController.overrideUserInterfaceStyle = interfaceStyle
            rootViewController.setNeedsStatusBarAppearanceUpdate()
        }
    }
}
