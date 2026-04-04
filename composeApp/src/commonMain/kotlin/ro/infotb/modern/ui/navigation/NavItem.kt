package ro.infotb.modern.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val screen: Screen,
    val labelKey: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val mainNavItems = listOf(
    NavItem(
        screen = Screen.Dashboard,
        labelKey = "nav.dashboard",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home
    ),
    NavItem(
        screen = Screen.Lines,
        labelKey = "nav.lines",
        icon = Icons.Outlined.Star,
        selectedIcon = Icons.Filled.Star
    ),
    NavItem(
        screen = Screen.Announcements,
        labelKey = "nav.announcements",
        icon = Icons.Outlined.Info,
        selectedIcon = Icons.Filled.Info
    ),
    NavItem(
        screen = Screen.Settings,
        labelKey = "nav.settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings
    )
)
