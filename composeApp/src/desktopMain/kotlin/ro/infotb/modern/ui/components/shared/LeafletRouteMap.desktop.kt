package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition

@Composable
actual fun LeafletRouteMap(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: String,
    segmentPath: String?,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map — ${stops.size} stops, ${vehicles.size} vehicles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
