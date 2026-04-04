package ro.infotb.modern.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition
import ro.infotb.modern.ui.components.shared.DirectionToggle
import ro.infotb.modern.ui.components.shared.LeafletRouteMap
import ro.infotb.modern.ui.components.shared.drawScrollbar
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.TransportBadge
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.ui.viewmodel.LineDetailViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(
    lineId: Long,
    viewModel: LineDetailViewModel,
    locale: Locale,
    onBack: () -> Unit,
    onNavigateToStop: (stopId: Long, lineId: Long, direction: Direction) -> Unit
) {
    val lineDetail by viewModel.lineDetail.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    var showFullMap by remember { mutableStateOf(false) }

    LaunchedEffect(lineId) {
        viewModel.loadLine(lineId)
    }

    // Full-screen map overlay
    if (showFullMap && lineDetail != null) {
        val detail = lineDetail!!
        Box(modifier = Modifier.fillMaxSize()) {
            LeafletRouteMap(
                stops = detail.stops,
                vehicles = vehicles,
                lineColor = detail.color,
                segmentPath = detail.segmentPath,
                modifier = Modifier.fillMaxSize()
            )
            // Close button
            IconButton(
                onClick = { showFullMap = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Close map",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lineDetail != null) {
                        LineBadge(
                            name = lineDetail!!.name,
                            color = lineDetail!!.color,
                            size = BadgeSize.MEDIUM
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "${t("lineDetail.linePrefix", locale)} ${lineDetail?.name ?: ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = t("lineDetail.backToLines", locale))
                }
            }
        )

        if (isLoading && lineDetail == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (lineDetail == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("lineDetail.notFound", locale),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalMuted.current
                )
            }
        } else {
            val detail = lineDetail!!

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Transport badge and station count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransportBadge(type = detail.type)
                    Text(
                        text = "${detail.stops.size} ${t("lineDetail.stations", locale)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Direction toggle
                DirectionToggle(
                    selected = direction,
                    turLabel = detail.directionNameTur ?: "",
                    returLabel = detail.directionNameRetur ?: "",
                    locale = locale,
                    onSelect = { viewModel.setDirection(it) }
                )

                Spacer(Modifier.height(12.dp))
            }

            // Announcements banner
            if (announcements.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF8E1)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF8F00),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = announcements.first().title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFF5D4037)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Live map button
            FilledTonalButton(
                onClick = { showFullMap = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (vehicles.isNotEmpty()) {
                        "${t("lineDetail.showMap", locale)} — ${vehicles.size} ${t("lineDetail.vehiclesActive", locale)}"
                    } else {
                        t("lineDetail.showMap", locale)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Stops list
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .drawScrollbar(listState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                itemsIndexed(detail.stops, key = { _, stop -> stop.id }) { index, stop ->
                    StopRow(
                        index = index,
                        stop = stop,
                        lineColor = detail.color,
                        isFirst = index == 0,
                        isLast = index == detail.stops.lastIndex,
                        vehicles = vehicles,
                        locale = locale,
                        onClick = {
                            onNavigateToStop(stop.id, detail.id, direction)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    index: Int,
    stop: Stop,
    lineColor: String,
    isFirst: Boolean,
    isLast: Boolean,
    vehicles: List<VehiclePosition>,
    locale: Locale,
    onClick: () -> Unit
) {
    val color = try {
        parseColor(lineColor)
    } catch (_: Exception) {
        Color(0xFF1E88E5)
    }

    // Check if any vehicle is near this stop (within ~200m)
    val nearbyVehicle = vehicles.any { vehicle ->
        val dist = haversineApprox(vehicle.lat, vehicle.lng, stop.lat, stop.lng)
        dist < 0.2 // 200m
    }
    val veryClose = vehicles.any { vehicle ->
        val dist = haversineApprox(vehicle.lat, vehicle.lng, stop.lat, stop.lng)
        dist < 0.05 // 50m
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left: number + connecting line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(color.copy(alpha = 0.3f))
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(color.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Right: stop info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (nearbyVehicle) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(if (veryClose) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (veryClose) Color(0xFF43A047) else Color(0xFFFFA726)
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (veryClose) t("lineDetail.vehicleClose", locale)
                        else t("lineDetail.vehicleApproaching", locale),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = if (veryClose) Color(0xFF43A047) else Color(0xFFFFA726)
                    )
                }
            }
            val desc = stop.description
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Simple distance approximation in km for nearby checks */
private fun haversineApprox(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = (lat2 - lat1) * 111.0
    val dLng = (lng2 - lng1) * 111.0 * 0.7 // rough cos(44deg)
    return kotlin.math.sqrt(dLat * dLat + dLng * dLng)
}
