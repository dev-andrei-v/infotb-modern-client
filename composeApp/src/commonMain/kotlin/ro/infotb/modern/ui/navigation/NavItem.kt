package ro.infotb.modern.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import ro.infotb.modern.ui.components.shared.DashboardGridFilledIcon
import ro.infotb.modern.ui.components.shared.DashboardGridOutlinedIcon
import ro.infotb.modern.ui.components.shared.LinesRouteFilledIcon
import ro.infotb.modern.ui.components.shared.LinesRouteOutlinedIcon
import ro.infotb.modern.ui.components.shared.MapFilledIcon
import ro.infotb.modern.ui.components.shared.MapOutlinedIcon
import ro.infotb.modern.ui.components.shared.PlannerSwapFilledIcon
import ro.infotb.modern.ui.components.shared.PlannerSwapOutlinedIcon

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
        icon = DashboardGridOutlinedIcon,
        selectedIcon = DashboardGridFilledIcon
    ),
    NavItem(
        screen = Screen.Lines,
        labelKey = "nav.lines",
        icon = LinesRouteOutlinedIcon,
        selectedIcon = LinesRouteFilledIcon
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

fun mainNavItemsFor(platform: String): List<NavItem> = if (platform == "Android") {
    listOf(
        NavItem(
            screen = Screen.Dashboard,
            labelKey = "nav.dashboard",
            icon = DashboardGridOutlinedIcon,
            selectedIcon = DashboardGridFilledIcon
        ),
        NavItem(
            screen = Screen.Lines,
            labelKey = "nav.lines",
            icon = LinesRouteOutlinedIcon,
            selectedIcon = LinesRouteFilledIcon
        ),
        NavItem(
            screen = Screen.NearbyMap,
            labelKey = "nav.map",
            icon = MapOutlinedIcon,
            selectedIcon = MapFilledIcon
        ),
        NavItem(
            screen = Screen.Planner,
            labelKey = "nav.planner",
            icon = PlannerSwapOutlinedIcon,
            selectedIcon = PlannerSwapFilledIcon
        ),
        NavItem(
            screen = Screen.Settings,
            labelKey = "nav.settings",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        )
    )
} else {
    mainNavItems
}
