package ro.infotb.modern.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineArrivalDetail
import ro.infotb.modern.location.GeoPoint
import ro.infotb.modern.location.distanceMeters
import ro.infotb.modern.ui.components.shared.*
import ro.infotb.modern.ui.viewmodel.LineDetailViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.nearbyStopIndices
import ro.infotb.modern.util.t

/** A navigation destination so opening a stop and returning preserves the map. */
@Composable
fun LineMapScreen(
    lineId: Long,
    initialDirection: Direction,
    viewModel: LineDetailViewModel,
    locale: Locale,
    onBack: () -> Unit,
    onNavigateToStop: (Long, Direction) -> Unit
) {
    val detail by viewModel.lineDetail.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val refreshing by viewModel.isRefreshingVehicles.collectAsState()
    val location by viewModel.userLocation.collectAsState()
    var selectedStopId by rememberSaveable { mutableStateOf<Long?>(null) }
    var arrival by remember { mutableStateOf<LineArrivalDetail?>(null) }
    var arrivalLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(lineId) {
        if (viewModel.lineDetail.value == null) {
            viewModel.setDirection(initialDirection)
            viewModel.loadLine(lineId)
        }
        viewModel.refreshLocation()
    }
    LaunchedEffect(selectedStopId, direction) {
        arrival = null
        arrivalLoaded = false
        val id = selectedStopId ?: return@LaunchedEffect
        while (true) {
            arrival = viewModel.arrivalAt(id)
            arrivalLoaded = true
            delay(10_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val route = detail
        if (route != null) {
            EmbeddedRouteMapView(route.stops, vehicles, route.color, route.segmentPath,
                selectedStopId, { selectedStopId = it }, Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("stopDetail.back", locale)) }
            }
            Spacer(Modifier.width(8.dp))
            if (route != null) {
                Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(route.name, route.color)
                        Spacer(Modifier.width(8.dp))
                        Text((if (direction == Direction.TUR) route.directionNameTur else route.directionNameRetur).orEmpty(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (route != null) {
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val selected = route.stops.firstOrNull { it.id == selectedStopId }
                val nearest = nearbyStopIndices(route.stops, location, 1, Int.MAX_VALUE).firstOrNull()?.let { route.stops[it] }
                if (selected != null) {
                    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(selected.name, fontWeight = FontWeight.Bold)
                                    Text(t("lineDetail.stopOf", locale).replace("{n}", "${route.stops.indexOf(selected) + 1}").replace("{total}", "${route.stops.size}"), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { selectedStopId = null }) { Icon(Icons.Default.Close, t("dashboard.close", locale)) }
                            }
                            val current = arrival
                            if (current != null && current.arrivingTime >= 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ArrivalTimeBadge(current.arrivingTime, current.isTimetable, BadgeSize.LARGE)
                                    current.arrivingTimes.drop(1).take(2).forEach { ArrivalTimeBadge(it.arrivingTime, it.isTimetable, BadgeSize.SMALL) }
                                }
                            } else if (!arrivalLoaded) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            } else {
                                Text(t(if (current == null) "widget.noData" else "widget.outOfService", locale))
                            }
                            FilledTonalButton(onClick = { onNavigateToStop(selected.id, direction) }, modifier = Modifier.fillMaxWidth()) {
                                Text(t("lineDetail.moreAboutStop", locale))
                            }
                        }
                    }
                } else if (nearest != null) {
                    Surface(onClick = { selectedStopId = nearest.id }, shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("lineDetail.nearestStop", locale), style = MaterialTheme.typography.labelSmall)
                                Text(nearest.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            location?.let { Text("${distanceMeters(it, GeoPoint(nearest.lat, nearest.lng)).toInt()} m", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DirectionToggle(direction, "", "", locale, onSelect = {
                                if (it != direction) {
                                    selectedStopId = null
                                    viewModel.setDirection(it)
                                }
                            })
                        }
                        IconButton(onClick = { viewModel.refreshVehicles() }, enabled = !refreshing) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, t("lineDetail.refreshVehicles", locale))
                        }
                    }
                }
            }
        }
    }
}
