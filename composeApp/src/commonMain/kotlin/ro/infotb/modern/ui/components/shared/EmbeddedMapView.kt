package ro.infotb.modern.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun EmbeddedMapView(
    lat: Double,
    lng: Double,
    label: String,
    zoom: Int = 16,
    modifier: Modifier = Modifier
)
