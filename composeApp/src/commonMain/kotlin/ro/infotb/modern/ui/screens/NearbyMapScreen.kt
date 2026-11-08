package ro.infotb.modern.ui.screens

import androidx.compose.runtime.Composable
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.location.PlatformLocation
import ro.infotb.modern.util.Locale

@Composable
expect fun NearbyMapScreen(
    repository: TransitRepository,
    platformLocation: PlatformLocation,
    locale: Locale,
    onNavigateToStop: (stopId: Long) -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit
)
