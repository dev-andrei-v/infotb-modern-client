package ro.infotb.modern.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineArrivalDetail
import ro.infotb.modern.data.model.TimetableHour
import ro.infotb.modern.ui.components.shared.ArrivalTimeBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.LeafletMap
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.ui.viewmodel.StopDetailViewModel
import ro.infotb.modern.currentHourOfDay
import ro.infotb.modern.currentMinuteOfHour
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stopId: Long,
    lineId: Long? = null,
    direction: Direction? = null,
    viewModel: StopDetailViewModel,
    locale: Locale,
    onBack: () -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit,
    onAddToDashboard: (stopId: Long, stopName: String) -> Unit,
    onSetNotification: (stopId: Long, stopName: String, lineId: Long, lineName: String, lineColor: String, direction: Direction, minutes: Int, isTimetable: Boolean) -> Unit
) {
    val stopInfo by viewModel.stopInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lineTimetables by viewModel.lineTimetables.collectAsState()
    val timetableLoading by viewModel.timetableLoading.collectAsState()
    val stopCoordinates by viewModel.stopCoordinates.collectAsState()
    val expandedTimetables = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(stopId) {
        viewModel.loadStop(stopId, lineId, direction)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stopInfo?.name ?: t("stopDetail.arrivals", locale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = t("stopDetail.back", locale))
                }
            }
        )

        if (isLoading && stopInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (stopInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("stopDetail.notFound", locale),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalMuted.current
                )
            }
        } else {
            val info = stopInfo!!

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                // Stop address
                if (!info.address.isNullOrBlank()) {
                    item(key = "address") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = LocalMuted.current,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = info.address.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalMuted.current
                            )
                        }
                    }
                }

                // Leaflet Map
                val coords = stopCoordinates
                if (coords != null) {
                    item(key = "map") {
                        LeafletMap(
                            lat = coords.lat,
                            lng = coords.lng,
                            label = info.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // Lines count
                item(key = "count") {
                    Text(
                        text = "${info.lines.size} ${t("stopDetail.lines", locale)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (info.lines.isEmpty()) {
                    item(key = "no_lines") {
                        Text(
                            text = t("stopDetail.noLines", locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalMuted.current,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }

                // Sort: primary line first (if navigated from line detail)
                val sortedLines = if (lineId != null) {
                    val primary = info.lines.filter { it.id == lineId }
                    val others = info.lines.filter { it.id != lineId }
                    primary + others
                } else {
                    info.lines
                }

                items(sortedLines, key = { "${it.id}_${it.direction.value}" }) { line ->
                    val timetableKey = "${line.id}_${line.direction.value}"
                    val isTimetableExpanded = expandedTimetables[timetableKey] == true
                    val isPrimary = line.id == lineId

                    // Determine which timetable to show
                    val timetableData = if (line.timetable.isNotEmpty()) {
                        line.timetable
                    } else {
                        lineTimetables[timetableKey]
                    }
                    val isTimetableLoading = timetableLoading.contains(timetableKey)

                    LineArrivalCard(
                        line = line,
                        isPrimary = isPrimary,
                        isTimetableExpanded = isTimetableExpanded,
                        timetableData = timetableData,
                        isTimetableLoading = isTimetableLoading,
                        locale = locale,
                        onNavigateToLine = { onNavigateToLine(line.id) },
                        onToggleTimetable = {
                            val newExpanded = !isTimetableExpanded
                            expandedTimetables[timetableKey] = newExpanded
                            // Fetch timetable on-demand if not already available
                            if (newExpanded && line.timetable.isEmpty() && !lineTimetables.containsKey(timetableKey)) {
                                viewModel.loadLineTimetable(line.id, line.direction)
                            }
                        },
                        onAddToDashboard = {
                            onAddToDashboard(stopId, info.name)
                        },
                        onSetNotification = {
                            onSetNotification(
                                stopId,
                                info.name,
                                line.id,
                                line.name,
                                line.color,
                                line.direction,
                                (line.arrivingTime / 60).toInt().coerceAtLeast(0),
                                line.isTimetable
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LineArrivalCard(
    line: LineArrivalDetail,
    isPrimary: Boolean,
    isTimetableExpanded: Boolean,
    timetableData: List<TimetableHour>?,
    isTimetableLoading: Boolean,
    locale: Locale,
    onNavigateToLine: () -> Unit,
    onToggleTimetable: () -> Unit,
    onAddToDashboard: () -> Unit,
    onSetNotification: () -> Unit
) {
    val borderColor = if (isPrimary) {
        try {
            parseColor(line.color)
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPrimary) Modifier.border(
                    2.dp,
                    borderColor,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: badge + direction + arrival time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable(onClick = onNavigateToLine)) {
                    LineBadge(
                        name = line.name,
                        color = line.color,
                        size = BadgeSize.MEDIUM
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.directionName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                ArrivalTimeBadge(
                    seconds = line.arrivingTime,
                    isTimetable = line.isTimetable,
                    size = BadgeSize.MEDIUM
                )
            }

            // Next arrivals (skip duplicating the first one already shown in header)
            if (line.arrivingTimes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${t("widget.nextArrivals", locale)}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    line.arrivingTimes.drop(1).forEach { arrival ->
                        ArrivalTimeBadge(
                            seconds = arrival.arrivingTime,
                            isTimetable = arrival.isTimetable,
                            size = BadgeSize.SMALL
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onAddToDashboard, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(t("stopDetail.addToDashboard", locale), fontSize = 11.sp, maxLines = 1)
                }
                TextButton(onClick = onSetNotification, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(t("stopDetail.notification", locale), fontSize = 11.sp, maxLines = 1)
                }
                TextButton(onClick = onToggleTimetable, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(t("stopDetail.timetable", locale), fontSize = 11.sp, maxLines = 1)
                    Icon(
                        if (isTimetableExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Expandable timetable
            AnimatedVisibility(
                visible = isTimetableExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                when {
                    isTimetableLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    timetableData != null && timetableData.isNotEmpty() -> {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "${t("stopDetail.timetableTitle", locale)} ${line.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            TimetableGrid(
                                timetable = timetableData,
                                locale = locale
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = t("widget.noData", locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMuted.current,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetableGrid(
    timetable: List<TimetableHour>,
    locale: Locale
) {
    val currentHour = currentHourOfDay()
    val currentMinute = currentMinuteOfHour()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Find the index of the current/next hour row for auto-scroll
    val currentHourIndex = remember(timetable, currentHour) {
        val exactIndex = timetable.indexOfFirst { (it.hour.toIntOrNull() ?: -1) == currentHour }
        if (exactIndex >= 0) exactIndex
        else timetable.indexOfFirst { (it.hour.toIntOrNull() ?: -1) > currentHour }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = t("timetable.hour", locale),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp)
            )
            Text(
                text = t("timetable.minutes", locale),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Scrollable timetable rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .verticalScroll(scrollState)
        ) {
            timetable.forEachIndexed { index, hour ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }

                val h = hour.hour.toIntOrNull() ?: -1
                val isCurrentHour = h == currentHour
                val isPastHour = h < currentHour

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrentHour) Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hour.hour,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentHour) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isPastHour) LocalMuted.current.copy(alpha = 0.5f)
                        else if (isCurrentHour) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(48.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        hour.minutes.forEach { minute ->
                            val minuteVal = minute.toIntOrNull() ?: 0
                            val isPastMinute = isPastHour || (isCurrentHour && minuteVal < currentMinute)
                            val isNextMinute = isCurrentHour && minuteVal >= currentMinute &&
                                    hour.minutes.none { m ->
                                        val mv = m.toIntOrNull() ?: 0
                                        mv in currentMinute until minuteVal
                                    }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(
                                        if (isNextMinute) Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        ) else Modifier
                                    )
                                    .background(
                                        when {
                                            isNextMinute -> MaterialTheme.colorScheme.primaryContainer
                                            isPastMinute -> Color.Transparent
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = minute.padStart(2, '0'),
                                    fontSize = 11.sp,
                                    fontWeight = if (isNextMinute) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isNextMinute -> MaterialTheme.colorScheme.primary
                                        isPastMinute -> LocalMuted.current.copy(alpha = 0.4f)
                                        else -> LocalMuted.current
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-scroll to current hour row
    LaunchedEffect(currentHourIndex) {
        if (currentHourIndex > 0) {
            coroutineScope.launch {
                // Approximate row height ~38dp; scroll so current row is near top
                scrollState.animateScrollTo(currentHourIndex * 100)
            }
        }
    }
}
