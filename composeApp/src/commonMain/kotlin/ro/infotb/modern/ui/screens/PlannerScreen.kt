package ro.infotb.modern.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.model.PlannerPlace
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.location.PlatformLocation
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.vehicleIcon
import ro.infotb.modern.ui.screens.planner.RouteJsonParser
import ro.infotb.modern.ui.screens.planner.RouteLeg
import ro.infotb.modern.ui.screens.planner.RouteOption
import ro.infotb.modern.ui.screens.planner.SavedTripRecord
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    repository: TransitRepository,
    platformLocation: PlatformLocation,
    preferencesStore: PreferencesStore,
    locale: Locale,
    onNavigateToLine: ((Long) -> Unit)? = null,
    onNavigateToStop: ((Long) -> Unit)? = null
) {
    var origin by remember { mutableStateOf<PlannerPlace?>(null) }
    var destination by remember { mutableStateOf<PlannerPlace?>(null) }

    var selectedModes by remember {
        mutableStateOf(TransportType.entries.filter { it != TransportType.SUBWAY || true }.toSet())
    }
    var accessible by remember { mutableStateOf(false) }
    var maxWalkDistance by remember { mutableStateOf(1000) }
    var showAdvanced by remember { mutableStateOf(false) }

    var isPlanning by remember { mutableStateOf(false) }
    var plannerError by remember { mutableStateOf<String?>(null) }
    var routeOptions by remember { mutableStateOf<List<RouteOption>?>(null) }

    // Place picker state
    var pickingTarget by remember { mutableStateOf<PlannerTarget?>(null) }

    // Saved trips state
    val json = remember { Json { ignoreUnknownKeys = true } }
    var savedTrips by remember {
        mutableStateOf<List<SavedTripRecord>>(
            try {
                preferencesStore.getSavedTrips()?.let {
                    json.decodeFromString<List<SavedTripRecord>>(it)
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        )
    }

    fun persistSavedTrips(trips: List<SavedTripRecord>) {
        savedTrips = trips
        try {
            preferencesStore.setSavedTrips(json.encodeToString(trips))
        } catch (_: Exception) {}
    }

    val coroutineScope = rememberCoroutineScope()

    fun findRoutes() {
        val start = origin ?: return
        val dest = destination ?: return

        isPlanning = true
        plannerError = null
        routeOptions = null

        coroutineScope.launch {
            try {
                val apiVehicleTypes = selectedModes.map { it.name }
                val rawResponse = repository.planRoutes(
                    start = start,
                    destination = dest,
                    maxWalkDistance = maxWalkDistance,
                    startTimeMs = currentTimeMillis(),
                    vehicleTypes = apiVehicleTypes,
                    accessible = accessible,
                    language = if (locale == Locale.RO) "ro" else "en"
                )
                val parsed = RouteJsonParser.parse(rawResponse)
                if (parsed.isEmpty()) {
                    plannerError = t("planner.noSearchResults", locale)
                } else {
                    routeOptions = parsed
                }
            } catch (e: Exception) {
                plannerError = e.message ?: t("planner.loadFailed", locale)
            } finally {
                isPlanning = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = t("planner.title", locale),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = t("planner.subtitle", locale),
                style = MaterialTheme.typography.bodySmall,
                color = LocalMuted.current
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Transport Modes & Options
            item(key = "modes_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = t("planner.vehicles", locale),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransportType.entries.forEach { type ->
                                val isSelected = selectedModes.contains(type)
                                val color = transportColor(type)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedModes = if (isSelected) {
                                            if (selectedModes.size > 1) selectedModes - type else selectedModes
                                        } else {
                                            selectedModes + type
                                        }
                                    },
                                    label = { Text(t("transport.${type.name}", locale)) },
                                    leadingIcon = {
                                        Icon(
                                            vehicleIcon(type),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
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

                        // Advanced options toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = t("planner.advanced", locale),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = LocalMuted.current
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = t("planner.accessibility", locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = accessible,
                                        onCheckedChange = { accessible = it }
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = t("planner.walkDistance", locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$maxWalkDistance m",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = maxWalkDistance.toFloat(),
                                    onValueChange = { maxWalkDistance = it.toInt() },
                                    valueRange = 250f..2000f,
                                    steps = 6
                                )
                            }
                        }
                    }
                }
            }

            // Error display
            if (plannerError != null) {
                item(key = "error_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = plannerError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Results Section
            val results = routeOptions
            if (results != null && results.isNotEmpty()) {
                item(key = "results_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = t("planner.routes", locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val s = origin ?: return@TextButton
                                val d = destination ?: return@TextButton
                                val newRecord = SavedTripRecord(
                                    id = "${s.name}_${d.name}_${currentTimeMillis()}",
                                    origin = s,
                                    destination = d,
                                    savedAtTimestamp = currentTimeMillis(),
                                    modes = selectedModes.map { it.name },
                                    accessible = accessible,
                                    maxWalkDistance = maxWalkDistance
                                )
                                persistSavedTrips(listOf(newRecord) + savedTrips.filterNot {
                                    it.origin.name == s.name && it.destination.name == d.name
                                })
                            }
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("planner.saveTrip", locale))
                        }
                    }
                }

                items(results, key = { it.id }) { option ->
                    RouteOptionCard(
                        option = option,
                        locale = locale,
                        onNavigateToLine = onNavigateToLine,
                        onNavigateToStop = onNavigateToStop
                    )
                }
            }

            // Saved Trips Section
            if (savedTrips.isNotEmpty()) {
                item(key = "saved_trips_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = t("planner.savedTrips", locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${savedTrips.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalMuted.current
                        )
                    }
                }

                items(savedTrips, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                origin = record.origin
                                destination = record.destination
                                accessible = record.accessible
                                maxWalkDistance = record.maxWalkDistance
                                if (record.modes.isNotEmpty()) {
                                    val mapped = record.modes
                                        .mapNotNull { m -> TransportType.entries.firstOrNull { it.name == m } }
                                        .toSet()
                                    if (mapped.isNotEmpty()) {
                                        selectedModes = mapped
                                    }
                                }
                                findRoutes()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${record.origin.name} → ${record.destination.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${record.maxWalkDistance}m walk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalMuted.current
                                )
                            }
                            IconButton(
                                onClick = {
                                    persistSavedTrips(savedTrips.filterNot { it.id == record.id })
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = t("widget.delete", locale),
                                    tint = LocalMuted.current,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Docked Trip Search Controls at the bottom (matching Swift tripControlsDock)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlaceDockButton(
                        label = t("planner.from", locale),
                        placeholder = t("planner.chooseOrigin", locale),
                        place = origin,
                        isOrigin = true,
                        modifier = Modifier.weight(1f),
                        onClick = { pickingTarget = PlannerTarget.ORIGIN }
                    )

                    IconButton(
                        onClick = {
                            val temp = origin
                            origin = destination
                            destination = temp
                        },
                        enabled = origin != null || destination != null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = t("planner.swap", locale),
                            tint = if (origin != null || destination != null) MaterialTheme.colorScheme.primary else LocalMuted.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    PlaceDockButton(
                        label = t("planner.to", locale),
                        placeholder = t("planner.chooseDestination", locale),
                        place = destination,
                        isOrigin = false,
                        modifier = Modifier.weight(1f),
                        onClick = { pickingTarget = PlannerTarget.DESTINATION }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { findRoutes() },
                    enabled = origin != null && destination != null && !isPlanning && selectedModes.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isPlanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(t("planner.findRoutes", locale), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Place picker dialog
    if (pickingTarget != null) {
        PlacePickerDialog(
            target = pickingTarget!!,
            repository = repository,
            platformLocation = platformLocation,
            locale = locale,
            onDismiss = { pickingTarget = null },
            onSelectPlace = { place ->
                if (pickingTarget == PlannerTarget.ORIGIN) origin = place
                else destination = place
                pickingTarget = null
            }
        )
    }
}

private enum class PlannerTarget { ORIGIN, DESTINATION }

@Composable
private fun PlaceDockButton(
    label: String,
    placeholder: String,
    place: PlannerPlace?,
    isOrigin: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            if (isOrigin) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                )
            } else {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMuted.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = place?.name ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (place != null) MaterialTheme.colorScheme.onSurface else LocalMuted.current
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteOptionCard(
    option: RouteOption,
    locale: Locale,
    onNavigateToLine: ((Long) -> Unit)?,
    onNavigateToStop: ((Long) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (option.durationText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = option.durationText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!option.summary.isNullOrBlank()) {
                Text(
                    text = option.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Transit Legs Strip
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                option.legs.forEachIndexed { idx, leg ->
                    if (idx > 0) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterVertically),
                            tint = LocalMuted.current
                        )
                    }
                    if (leg.isWalk) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = LocalMuted.current
                            )
                            if (!leg.distanceText.isNullOrEmpty()) {
                                Spacer(Modifier.width(3.dp))
                                Text(leg.distanceText, fontSize = 11.sp, color = LocalMuted.current)
                            }
                        }
                    } else if (!leg.lineName.isNullOrEmpty()) {
                        LineBadge(
                            name = leg.lineName,
                            color = leg.lineColor ?: "#1E88E5",
                            size = BadgeSize.SMALL
                        )
                    }
                }
            }

            // Expandable details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))

                    option.legs.forEach { leg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (leg.isWalk) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = LocalMuted.current,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Mers pe jos: ${leg.distanceText.orEmpty()} (${leg.durationText.orEmpty()})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalMuted.current
                                    )
                                }
                            } else {
                                LineBadge(
                                    name = leg.lineName.orEmpty(),
                                    color = leg.lineColor ?: "#1E88E5",
                                    size = BadgeSize.SMALL
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!leg.fromStop.isNullOrEmpty()) {
                                        Text(
                                            text = "De la: ${leg.fromStop}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (!leg.toStop.isNullOrEmpty()) {
                                        Text(
                                            text = "Până la: ${leg.toStop}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalMuted.current
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacePickerDialog(
    target: PlannerTarget,
    repository: TransitRepository,
    platformLocation: PlatformLocation,
    locale: Locale,
    onDismiss: () -> Unit,
    onSelectPlace: (PlannerPlace) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlannerPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                t(if (target == PlannerTarget.ORIGIN) "planner.chooseOrigin" else "planner.chooseDestination", locale),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        debounceJob?.cancel()
                        if (query.trim().length >= 2) {
                            isSearching = true
                            debounceJob = coroutineScope.launch {
                                delay(300)
                                results = try {
                                    repository.searchPlannerPlaces(query.trim())
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                isSearching = false
                            }
                        } else {
                            results = emptyList()
                            isSearching = false
                        }
                    },
                    placeholder = { Text(t("planner.searchPlaces", locale)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; results = emptyList() }) {
                                Icon(Icons.Default.Close, contentDescription = t("common.clear", locale))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                // Current location button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            coroutineScope.launch {
                                val fix = platformLocation.current()
                                if (fix != null) {
                                    onSelectPlace(
                                        PlannerPlace(
                                            name = t("planner.currentLocation", locale),
                                            lat = fix.lat,
                                            lng = fix.lng,
                                            type = "CURRENT_LOCATION"
                                        )
                                    )
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = t("planner.currentLocation", locale),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        items(results) { place ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlace(place) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = LocalMuted.current,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val desc = place.description
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
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("planner.cancel", locale))
            }
        }
    )
}
