package ro.infotb.modern.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition

@Composable
expect fun EmbeddedRouteMapView(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: String,
    segmentPath: String?,
    selectedStopId: Long? = null,
    onStopSelect: (Long) -> Unit = {},
    modifier: Modifier = Modifier
)
