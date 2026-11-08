package ro.infotb.modern.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.location.PlatformLocation
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.util.Locale

@Composable
actual fun NearbyMapScreen(
    repository: TransitRepository,
    platformLocation: PlatformLocation,
    locale: Locale,
    onNavigateToStop: (stopId: Long) -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Harta live din apropiere este optimizată pentru dispozitive mobile.",
            color = LocalMuted.current
        )
    }
}
