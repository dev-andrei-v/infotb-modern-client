package ro.infotb.modern.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition

@Composable
expect fun LeafletRouteMap(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: String,
    segmentPath: String?,
    modifier: Modifier = Modifier
)
