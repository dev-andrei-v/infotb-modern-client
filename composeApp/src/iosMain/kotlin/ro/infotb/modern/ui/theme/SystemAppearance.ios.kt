package ro.infotb.modern.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyleLight

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SystemAppearance(isDark: Boolean) {
    SideEffect {
        val interfaceStyle = if (isDark) UIUserInterfaceStyleDark else UIUserInterfaceStyleLight
        UIApplication.sharedApplication.keyWindow?.rootViewController?.let { rootViewController ->
            rootViewController.overrideUserInterfaceStyle = interfaceStyle
            rootViewController.setNeedsStatusBarAppearanceUpdate()
        }
    }
}
