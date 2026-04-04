package ro.infotb.modern.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Lines : Screen("lines")
    data class LineDetail(val lineId: Long) : Screen("lines/$lineId") {
        companion object {
            const val ROUTE = "lines/{lineId}"
        }
    }
    data class StopDetail(
        val stopId: Long,
        val lineId: Long? = null,
        val direction: Int? = null
    ) : Screen(
        buildString {
            append("stops/$stopId")
            val params = mutableListOf<String>()
            if (lineId != null) params.add("lineId=$lineId")
            if (direction != null) params.add("direction=$direction")
            if (params.isNotEmpty()) append("?${params.joinToString("&")}")
        }
    ) {
        companion object {
            const val ROUTE = "stops/{stopId}?lineId={lineId}&direction={direction}"
        }
    }
    data object Announcements : Screen("announcements")
    data object Notifications : Screen("notifications")
    data object Settings : Screen("settings")
}
