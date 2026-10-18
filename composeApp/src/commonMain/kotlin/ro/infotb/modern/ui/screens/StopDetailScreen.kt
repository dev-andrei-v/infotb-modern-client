package ro.infotb.modern.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import ro.infotb.modern.ui.components.shared.HERO_ARRIVAL_MIN_WIDTH
import ro.infotb.modern.ui.components.shared.LeafletMap
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.RealtimeColor
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.ui.viewmodel.StopDetailViewModel
import ro.infotb.modern.currentHourOfDay
import ro.infotb.modern.currentMinuteOfHour
import ro.infotb.modern.data.store.NotificationSubscription
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
    activeSubscriptions: List<NotificationSubscription> = emptyList(),
    onBack: () -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit,
    onAddToDashboard: (stopId: Long, stopName: String) -> Unit,
    onSetNotification: (stopId: Long, stopName: String, lineId: Long, lineName: String, lineColor: String, direction: Direction, minutes: Int, isTimetable: Boolean) -> Unit,
    onStopNotification: (subscriptionId: String) -> Unit = {},
    onRefresh: (() -> Unit)? = null
) {
    val stopInfo by viewModel.stopInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    viewModel.refreshNow()
                    onRefresh?.invoke()
                },
                modifier = Modifier.weight(1f)
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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

                // Lines count + the one place to add this stop to the dashboard
                item(key = "count") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${info.lines.size} ${t("stopDetail.lines", locale)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { onAddToDashboard(stopId, info.name) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("stopDetail.addToDashboard", locale), fontSize = 12.sp, maxLines = 1)
                        }
                    }
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

                // Primary line first (when opened from a line), then by arrival, out-of-service last
                val sortedLines = info.lines.sortedWith(
                    compareBy<LineArrivalDetail> { if (it.id == lineId) 0 else 1 }
                        .thenBy { if (it.arrivingTime >= 0) 0 else 1 }
                        .thenBy { it.arrivingTime }
                )

                if (sortedLines.isNotEmpty()) {
                    item(key = "lines") {
                        StopLinesCard(
                            lines = sortedLines,
                            primaryLineId = lineId,
                            locale = locale,
                            isTracking = { line ->
                                activeSubscriptions.any { sub ->
                                    sub.stopId == stopId && sub.lineId == line.id &&
                                        sub.direction == line.direction && sub.enabled
                                }
                            },
                            isTimetableExpanded = { line ->
                                expandedTimetables["${line.id}_${line.direction.value}"] == true
                            },
                            timetableFor = { line ->
                                val key = "${line.id}_${line.direction.value}"
                                if (line.timetable.isNotEmpty()) line.timetable else lineTimetables[key]
                            },
                            isTimetableLoading = { line ->
                                "${line.id}_${line.direction.value}" in timetableLoading
                            },
                            onNavigateToLine = { line -> onNavigateToLine(line.id) },
                            onToggleTimetable = { line ->
                                val key = "${line.id}_${line.direction.value}"
                                val newExpanded = expandedTimetables[key] != true
                                expandedTimetables[key] = newExpanded
                                // Fetch timetable on-demand if not already available
                                if (newExpanded && line.timetable.isEmpty() && !lineTimetables.containsKey(key)) {
                                    viewModel.loadLineTimetable(line.id, line.direction)
                                }
                            },
                            onSetNotification = { line ->
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
                            },
                            onStopNotification = { line ->
                                activeSubscriptions.find { sub ->
                                    sub.stopId == stopId && sub.lineId == line.id &&
                                        sub.direction == line.direction && sub.enabled
                                }?.let { onStopNotification(it.id) }
                            }
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Every line at the stop in one card, laid out like a dashboard card: a colour
 * strip for the line arriving next, one row per line with the big arrival on
 * the right, and the timetable folding out under its row.
 */
@Composable
private fun StopLinesCard(
    lines: List<LineArrivalDetail>,
    primaryLineId: Long?,
    locale: Locale,
    isTracking: (LineArrivalDetail) -> Boolean,
    isTimetableExpanded: (LineArrivalDetail) -> Boolean,
    timetableFor: (LineArrivalDetail) -> List<TimetableHour>?,
    isTimetableLoading: (LineArrivalDetail) -> Boolean,
    onNavigateToLine: (LineArrivalDetail) -> Unit,
    onToggleTimetable: (LineArrivalDetail) -> Unit,
    onSetNotification: (LineArrivalDetail) -> Unit,
    onStopNotification: (LineArrivalDetail) -> Unit
) {
    val fallbackAccent = lines.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
        ?.let { transportColor(it) } ?: MaterialTheme.colorScheme.primary
    val accent = lines.firstOrNull { it.arrivingTime >= 0 }
        ?.let { runCatching { parseColor(it.color) }.getOrNull() }
        ?: fallbackAccent

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    StopLineRow(
                        line = line,
                        isPrimary = line.id == primaryLineId,
                        isTracking = isTracking(line),
                        isTimetableExpanded = isTimetableExpanded(line),
                        timetableData = timetableFor(line),
                        isTimetableLoading = isTimetableLoading(line),
                        locale = locale,
                        onNavigateToLine = { onNavigateToLine(line) },
                        onToggleTimetable = { onToggleTimetable(line) },
                        onSetNotification = { onSetNotification(line) },
                        onStopNotification = { onStopNotification(line) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StopLineRow(
    line: LineArrivalDetail,
    isPrimary: Boolean,
    isTracking: Boolean,
    isTimetableExpanded: Boolean,
    timetableData: List<TimetableHour>?,
    isTimetableLoading: Boolean,
    locale: Locale,
    onNavigateToLine: () -> Unit,
    onToggleTimetable: () -> Unit,
    onSetNotification: () -> Unit,
    onStopNotification: () -> Unit
) {
    val inService = line.arrivingTime >= 0
    val isImminent = inService && line.arrivingTime < 120
    val followUps = if (inService) line.arrivingTimes.drop(1).take(3) else emptyList()
    val lineTint = remember(line.color) { runCatching { parseColor(line.color) }.getOrNull() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // The line this screen was opened for gets a faint wash of its own colour
                if (isPrimary && lineTint != null) Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(lineTint.copy(alpha = 0.08f))
                    .padding(horizontal = 4.dp)
                else Modifier
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable(onClick = onNavigateToLine)) {
                LineBadge(name = line.name, color = line.color, size = BadgeSize.MEDIUM)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.directionName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (followUps.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        followUps.forEach { arrival ->
                            ArrivalTimeBadge(
                                seconds = arrival.arrivingTime,
                                isTimetable = arrival.isTimetable,
                                size = BadgeSize.SMALL
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onToggleTimetable, modifier = Modifier.size(28.dp)) {
                if (isTimetableLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = t("stopDetail.timetable", locale),
                        tint = if (isTimetableExpanded) MaterialTheme.colorScheme.primary else LocalMuted.current,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (inService) {
                IconButton(
                    onClick = if (isTracking) onStopNotification else onSetNotification,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = if (isTracking) t("stopDetail.stopTracking", locale)
                        else t("stopDetail.notification", locale),
                        tint = if (isTracking) MaterialTheme.colorScheme.error else LocalMuted.current,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = if (isImminent) Modifier
                        .border(1.5.dp, RealtimeColor, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                    else Modifier
                ) {
                    ArrivalTimeBadge(
                        seconds = line.arrivingTime,
                        isTimetable = line.isTimetable,
                        size = BadgeSize.LARGE,
                        modifier = Modifier.widthIn(min = HERO_ARRIVAL_MIN_WIDTH)
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = t("widget.outOfService", locale),
                        fontSize = 11.sp,
                        color = LocalMuted.current
                    )
                }
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
