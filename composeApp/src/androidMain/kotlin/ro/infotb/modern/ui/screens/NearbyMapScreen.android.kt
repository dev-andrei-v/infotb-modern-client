package ro.infotb.modern.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.Line
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.StopInfo
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.location.PlatformLocation
import ro.infotb.modern.ui.components.shared.ArrivalTimeBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.vehicleIcon
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.decodePolyline
import ro.infotb.modern.util.t

private const val BUCHAREST_LAT = 44.435
private const val BUCHAREST_LNG = 26.10
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
actual fun NearbyMapScreen(
    repository: TransitRepository,
    platformLocation: PlatformLocation,
    locale: Locale,
    onNavigateToStop: (stopId: Long) -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var isLoadingStops by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableStateOf(13.5) }
    var currentCenter by remember { mutableStateOf(LatLng(BUCHAREST_LAT, BUCHAREST_LNG)) }

    var selectedStop by remember { mutableStateOf<Stop?>(null) }
    var selectedStopPreview by remember { mutableStateOf<StopInfo?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    var selectedLineId by remember { mutableStateOf<Long?>(null) }
    val renderedPolylines = remember { mutableListOf<Polyline>() }

    // Stop lines & routes cache
    val stopLinesCache = remember { mutableMapOf<Long, List<Line>>() }
    var nearbyLines by remember { mutableStateOf<List<Line>>(emptyList()) }
    var isLoadingNearbyLines by remember { mutableStateOf(false) }
    var routePolylines by remember { mutableStateOf<Map<Long, List<List<LatLng>>>>(emptyMap()) }

    var selectedTypes by remember {
        mutableStateOf(TransportType.entries.toSet())
    }
    var searchQuery by remember { mutableStateOf("") }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    // Markers cache to avoid recreating
    val markersMap = remember { mutableMapOf<Long, Marker>() }

    // Init MapLibre once
    remember { MapLibre.getInstance(context) }

    fun loadNearbyLinesAndRoutes(stops: List<Stop>, center: LatLng) {
        if (stops.isEmpty()) return
        coroutineScope.launch {
            isLoadingNearbyLines = true
            try {
                val nearest = stops
                    .sortedBy { hypot(it.lat - center.latitude, it.lng - center.longitude) }
                    .take(10)

                val linesFound = mutableListOf<Line>()
                for (stop in nearest) {
                    val cached = stopLinesCache[stop.id]
                    if (cached != null) {
                        linesFound.addAll(cached)
                    } else {
                        try {
                            val fetched = repository.getStopLines(stop.id)
                            stopLinesCache[stop.id] = fetched
                            linesFound.addAll(fetched)
                        } catch (_: Exception) {}
                    }
                }

                val unique = linesFound
                    .distinctBy { it.id }
                    .filter { line -> line.type in selectedTypes }
                    .sortedWith(compareBy<Line> {
                        it.name.filter { c -> c.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
                    }.thenBy { it.name })

                nearbyLines = unique

                val linesToFetch = if (selectedLineId != null) {
                    unique.filter { it.id == selectedLineId }
                } else {
                    unique.take(5)
                }

                val newRouteMap = routePolylines.toMutableMap()
                for (line in linesToFetch) {
                    if (!newRouteMap.containsKey(line.id)) {
                        val segments = mutableListOf<List<LatLng>>()
                        try {
                            val tur = repository.getLineDirection(line.id, Direction.TUR)
                            val path = tur.segmentPath
                            if (!path.isNullOrBlank()) {
                                val pts = decodePolyline(path).map { LatLng(it.first, it.second) }
                                if (pts.size >= 2) segments.add(pts)
                            } else if (tur.stops.size >= 2) {
                                segments.add(tur.stops.map { LatLng(it.lat, it.lng) })
                            }
                        } catch (_: Exception) {}

                        try {
                            val retur = repository.getLineDirection(line.id, Direction.RETUR)
                            val path = retur.segmentPath
                            if (!path.isNullOrBlank()) {
                                val pts = decodePolyline(path).map { LatLng(it.first, it.second) }
                                if (pts.size >= 2) segments.add(pts)
                            } else if (retur.stops.size >= 2) {
                                segments.add(retur.stops.map { LatLng(it.lat, it.lng) })
                            }
                        } catch (_: Exception) {}

                        if (segments.isNotEmpty()) {
                            newRouteMap[line.id] = segments
                        }
                    }
                }
                routePolylines = newRouteMap
            } catch (_: Exception) {
            } finally {
                isLoadingNearbyLines = false
            }
        }
    }

    fun selectLine(line: Line) {
        if (selectedLineId == line.id) {
            selectedLineId = null
        } else {
            selectedLineId = line.id
            coroutineScope.launch {
                var segments = routePolylines[line.id]
                if (segments == null) {
                    val loaded = mutableListOf<List<LatLng>>()
                    try {
                        val tur = repository.getLineDirection(line.id, Direction.TUR)
                        val path = tur.segmentPath
                        if (!path.isNullOrBlank()) {
                            val pts = decodePolyline(path).map { LatLng(it.first, it.second) }
                            if (pts.size >= 2) loaded.add(pts)
                        } else if (tur.stops.size >= 2) {
                            loaded.add(tur.stops.map { LatLng(it.lat, it.lng) })
                        }
                    } catch (_: Exception) {}

                    try {
                        val retur = repository.getLineDirection(line.id, Direction.RETUR)
                        val path = retur.segmentPath
                        if (!path.isNullOrBlank()) {
                            val pts = decodePolyline(path).map { LatLng(it.first, it.second) }
                            if (pts.size >= 2) loaded.add(pts)
                        } else if (retur.stops.size >= 2) {
                            loaded.add(retur.stops.map { LatLng(it.lat, it.lng) })
                        }
                    } catch (_: Exception) {}

                    if (loaded.isNotEmpty()) {
                        routePolylines = routePolylines + (line.id to loaded)
                        segments = loaded
                    }
                }

                val map = mapInstance
                if (map != null && !segments.isNullOrEmpty()) {
                    val allPts = segments.flatten()
                    if (allPts.isNotEmpty()) {
                        val boundsBuilder = LatLngBounds.Builder()
                        allPts.forEach { boundsBuilder.include(it) }
                        try {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                        } catch (_: Exception) {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(allPts.first(), 14.5))
                        }
                    }
                }
            }
        }
    }

    fun refreshStopsInViewport(map: MapLibreMap) {
        val bounds = map.projection.visibleRegion.latLngBounds
        val center = map.cameraPosition.target ?: LatLng(BUCHAREST_LAT, BUCHAREST_LNG)
        currentZoom = map.cameraPosition.zoom
        currentCenter = center

        val maxSpanLat = 0.08
        val maxSpanLng = 0.10
        val south = maxOf(bounds.latitudeSouth, center.latitude - maxSpanLat)
        val north = minOf(bounds.latitudeNorth, center.latitude + maxSpanLat)
        val west = maxOf(bounds.longitudeWest, center.longitude - maxSpanLng)
        val east = minOf(bounds.longitudeEast, center.longitude + maxSpanLng)

        isLoadingStops = true
        coroutineScope.launch {
            try {
                val stops = repository.getStopsInBounds(
                    south = south,
                    west = west,
                    north = north,
                    east = east
                )
                loadedStops = stops
                loadNearbyLinesAndRoutes(stops, center)
            } catch (_: Exception) {
            } finally {
                isLoadingStops = false
            }
        }
    }

    fun selectStopOnMap(stop: Stop, map: MapLibreMap) {
        selectedStop = stop
        selectedStopPreview = null
        isLoadingPreview = true

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(stop.lat, stop.lng), 15.5)
        )

        coroutineScope.launch {
            bottomSheetState.expand()
            try {
                selectedStopPreview = repository.getStopInfo(stop.id, maxAgeMs = 0)
            } catch (_: Exception) {
            } finally {
                isLoadingPreview = false
            }
        }
    }

    // Filtered stops with zoom density limiter
    val filteredStops = remember(loadedStops, selectedTypes, searchQuery, currentZoom, currentCenter, selectedStop) {
        val matching = loadedStops.filter { stop ->
            val matchesSearch = searchQuery.isBlank() ||
                stop.name.contains(searchQuery, ignoreCase = true)
            matchesSearch
        }.sortedBy { stop ->
            val dLat = stop.lat - currentCenter.latitude
            val dLng = stop.lng - currentCenter.longitude
            dLat * dLat + dLng * dLng
        }

        val limited = when {
            currentZoom < 12.0 -> {
                // Far zoomed out: only show Subway stations and at most 10 nearest surface stops
                val metro = matching.filter { it.type == "SUBWAY_STATION" }
                val surface = matching.filter { it.type != "SUBWAY_STATION" }.take(10)
                metro + surface
            }
            currentZoom < 13.5 -> {
                // Medium zoom: Subway stations + 25 nearest surface stops
                val metro = matching.filter { it.type == "SUBWAY_STATION" }
                val surface = matching.filter { it.type != "SUBWAY_STATION" }.take(25)
                metro + surface
            }
            currentZoom < 14.5 -> {
                // Moderate zoom: Subway stations + 45 nearest surface stops
                val metro = matching.filter { it.type == "SUBWAY_STATION" }
                val surface = matching.filter { it.type != "SUBWAY_STATION" }.take(45)
                metro + surface
            }
            else -> {
                // Close zoom (< 14.5): up to 60 nearest stops
                matching.take(60)
            }
        }

        if (selectedStop != null && limited.none { it.id == selectedStop?.id }) {
            limited + selectedStop!!
        } else {
            limited
        }
    }

    // Grouped stations for sheet
    val groupedStations = remember(filteredStops) {
        filteredStops.groupBy { it.name }.map { (name, stops) ->
            StationGroup(
                name = name,
                stops = stops,
                isMetro = stops.any { it.type == "SUBWAY_STATION" }
            )
        }.sortedBy { it.name }
    }

    // Update polylines on MapLibre map (dual-stroke outline + color, matching iOS)
    LaunchedEffect(routePolylines, selectedLineId, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        renderedPolylines.forEach { map.removePolyline(it) }
        renderedPolylines.clear()

        val linesToRender = if (selectedLineId != null) {
            nearbyLines.filter { it.id == selectedLineId }
        } else {
            nearbyLines.filter { routePolylines.containsKey(it.id) }
        }

        linesToRender.forEach { line ->
            val segments = routePolylines[line.id] ?: return@forEach
            val isFocused = selectedLineId == line.id
            val parsedColor = parseLineColor(line.color)

            segments.forEach { points ->
                if (points.size >= 2) {
                    // White outline casing for crisp contrast
                    val outlineWidth = if (isFocused) 9f else 6.5f
                    val outlineColor = if (isFocused) {
                        android.graphics.Color.WHITE
                    } else if (selectedLineId != null) {
                        android.graphics.Color.argb(70, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(220, 255, 255, 255)
                    }

                    val outline = map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(outlineColor)
                            .width(outlineWidth)
                    )
                    renderedPolylines.add(outline)

                    // Core line stroke with transit color
                    val coreWidth = if (isFocused) 6f else 4f
                    val coreColor = if (isFocused) {
                        parsedColor
                    } else if (selectedLineId != null) {
                        adjustAlpha(parsedColor, 0.3f)
                    } else {
                        parsedColor
                    }

                    val core = map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(coreColor)
                            .width(coreWidth)
                    )
                    renderedPolylines.add(core)
                }
            }
        }
    }

    // Update markers on MapLibre map when stops change
    LaunchedEffect(filteredStops, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val iconFactory = IconFactory.getInstance(context)

        // Clear existing markers
        markersMap.values.forEach { map.removeMarker(it) }
        markersMap.clear()

        // Create marker icons
        val metroIcon = iconFactory.fromBitmap(createMetroMarkerBitmap())
        val surfaceIcon = iconFactory.fromBitmap(createSurfaceMarkerBitmap())

        filteredStops.forEach { stop ->
            val isMetro = stop.type == "SUBWAY_STATION"
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.lat, stop.lng))
                    .title(stop.name)
                    .snippet(stop.id.toString())
                    .icon(if (isMetro) metroIcon else surfaceIcon)
            )
            markersMap[stop.id] = marker
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 180.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 560.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // Drag handle spacing
                Spacer(Modifier.height(4.dp))

                // Transport filter chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    TransportType.entries.forEach { type ->
                        val isSelected = selectedTypes.contains(type)
                        val color = transportColor(type)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTypes = if (isSelected) {
                                    if (selectedTypes.size > 1) selectedTypes - type else selectedTypes
                                } else {
                                    selectedTypes + type
                                }
                            },
                            label = { Text(t("transport.${type.name}", locale), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    vehicleIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) color else LocalMuted.current
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.15f),
                                selectedLabelColor = color
                            )
                        )
                    }
                }

                // Nearby Lines Strip (matching iOS lineStrip)
                if (nearbyLines.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = t("map.nearbyLines", locale),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${nearbyLines.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (isLoadingNearbyLines) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            item(key = "all_routes") {
                                FilterChip(
                                    selected = selectedLineId == null,
                                    onClick = { selectedLineId = null },
                                    label = { Text(t("map.nearestRoutes", locale), fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            items(nearbyLines, key = { it.id }) { line ->
                                val isSelected = selectedLineId == line.id
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.clickable { selectLine(line) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        LineBadge(
                                            name = line.name,
                                            color = line.color,
                                            size = BadgeSize.SMALL
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // If a specific line is selected: Route Highlight Banner
                val currentLine = nearbyLines.firstOrNull { it.id == selectedLineId }
                if (currentLine != null && selectedStop == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            LineBadge(
                                name = currentLine.name,
                                color = currentLine.color,
                                size = BadgeSize.MEDIUM
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t("map.routeSelected", locale),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = t("map.routeHint", locale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalMuted.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { selectedLineId = null }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // If a stop is selected: Stop Preview View
                val currentStop = selectedStop
                if (currentStop != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (currentStop.type == "SUBWAY_STATION") Color(0xFF0066A4) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentStop.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = t(if (currentStop.type == "SUBWAY_STATION") "map.metroEntrance" else "map.surfaceStop", locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalMuted.current
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedStop = null
                                        selectedStopPreview = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = t("common.clear", locale),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (isLoadingPreview) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                val preview = selectedStopPreview
                                if (preview != null && preview.lines.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        preview.lines.take(5).forEach { line ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onNavigateToLine(line.id) }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                LineBadge(
                                                    name = line.name,
                                                    color = line.color,
                                                    size = BadgeSize.SMALL
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = line.directionName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalMuted.current,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                ArrivalTimeBadge(
                                                    seconds = line.arrivingTime,
                                                    isTimetable = line.isTimetable,
                                                    size = BadgeSize.SMALL
                                                )
                                            }
                                        }
                                    }
                                } else if (preview != null) {
                                    Text(
                                        text = t("widget.noData", locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalMuted.current,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { onNavigateToStop(currentStop.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(t("lineDetail.moreAboutStop", locale))
                            }
                        }
                    }
                } else {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(t("map.searchNearby", locale), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = t("common.clear", locale), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Stations list
                    if (groupedStations.isEmpty() && !isLoadingStops) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("map.noStops", locale),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalMuted.current
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(groupedStations, key = { it.name }) { station ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val firstStop = station.stops.firstOrNull() ?: return@clickable
                                            val map = mapInstance ?: return@clickable
                                            selectStopOnMap(firstStop, map)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (station.isMetro) Color(0xFF0066A4) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val stationLines = station.stops
                                            .flatMap { stopLinesCache[it.id] ?: emptyList() }
                                            .distinctBy { it.id }
                                        if (stationLines.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                stationLines.take(5).forEach { line ->
                                                    LineBadge(
                                                        name = line.name,
                                                        color = line.color,
                                                        size = BadgeSize.SMALL
                                                    )
                                                }
                                                if (stationLines.size > 5) {
                                                    Text(
                                                        text = "+${stationLines.size - 5}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = LocalMuted.current,
                                                        modifier = Modifier.align(Alignment.CenterVertically)
                                                    )
                                                }
                                            }
                                        } else if (station.stops.size > 1) {
                                            Text(
                                                text = t("map.platformCount", locale).replace("{n}", "${station.stops.size}"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LocalMuted.current
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = LocalMuted.current,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MapLibre native map view
            val mapView = remember {
                MapView(context).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        mapInstance = map
                        map.setStyle(MAP_STYLE_URL) {
                            coroutineScope.launch {
                                val userLoc = platformLocation.current()
                                val initialLat = userLoc?.lat ?: BUCHAREST_LAT
                                val initialLng = userLoc?.lng ?: BUCHAREST_LNG
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(initialLat, initialLng))
                                    .zoom(13.5)
                                    .build()

                                refreshStopsInViewport(map)
                            }

                            map.addOnCameraIdleListener {
                                currentZoom = map.cameraPosition.zoom
                                currentCenter = map.cameraPosition.target ?: LatLng(BUCHAREST_LAT, BUCHAREST_LNG)
                                refreshStopsInViewport(map)
                            }

                            map.setOnMarkerClickListener { marker ->
                                val stopId = marker.snippet?.toLongOrNull() ?: return@setOnMarkerClickListener true
                                val stop = loadedStops.firstOrNull { it.id == stopId } ?: return@setOnMarkerClickListener true
                                selectStopOnMap(stop, map)
                                true
                            }
                        }
                    }
                }
            }

            // Lifecycle forwarding
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, mapView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> mapView.onStart()
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        Lifecycle.Event.ON_STOP -> mapView.onStop()
                        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    mapView.onDestroy()
                }
            }

            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Floating controls: My Location button
            FloatingControls(
                isLoading = isLoadingStops,
                onMyLocation = {
                    val map = mapInstance ?: return@FloatingControls
                    coroutineScope.launch {
                        val loc = platformLocation.current()
                        if (loc != null) {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), 15.0)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

private data class StationGroup(
    val name: String,
    val stops: List<Stop>,
    val isMetro: Boolean
)

@Composable
private fun FloatingControls(
    isLoading: Boolean,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = onMyLocation,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isLoading) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun createMetroMarkerBitmap(): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0066A4")
        style = Paint.Style.FILL
    }
    val rect = RectF(4f, 4f, size - 4f, size - 4f)
    canvas.drawRoundRect(rect, 12f, 12f, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val yPos = (canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText("M", canvas.width / 2f, yPos, textPaint)

    return bitmap
}

private fun createSurfaceMarkerBitmap(): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C92335")
        style = Paint.Style.FILL
    }
    val rect = RectF(4f, 4f, size - 4f, size - 4f)
    canvas.drawRoundRect(rect, 14f, 14f, paint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(rect, 14f, 14f, strokePaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(canvas.width / 2f, canvas.height / 2f, 10f, innerPaint)

    return bitmap
}

private fun parseLineColor(hex: String?, fallback: Int = android.graphics.Color.parseColor("#1565C0")): Int {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        android.graphics.Color.parseColor(clean)
    } catch (_: Exception) {
        fallback
    }
}

private fun adjustAlpha(color: Int, factor: Float): Int {
    val alpha = (android.graphics.Color.alpha(color) * factor).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color)
    )
}
