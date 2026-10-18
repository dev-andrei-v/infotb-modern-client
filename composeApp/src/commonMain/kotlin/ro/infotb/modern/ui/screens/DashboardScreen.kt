package ro.infotb.modern.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
// Lock = hidden, Check = visible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.currentHourOfDay
import ro.infotb.modern.currentMinuteOfHour
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.data.store.WidgetSize
import ro.infotb.modern.ui.theme.RealtimeColor
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.ui.viewmodel.StopCardState
import kotlinx.coroutines.delay
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineArrivalDetail
import ro.infotb.modern.data.model.StopInfo
import ro.infotb.modern.data.model.TimetableHour
import ro.infotb.modern.data.store.WidgetConfig
import ro.infotb.modern.ui.components.shared.ArrivalTimeBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.EmptyState
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.NotificationDialog
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.viewmodel.DashboardViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    notificationStore: NotificationStore,
    useDirectNotificationAction: Boolean,
    locale: Locale,
    onNavigateToStop: (stopId: Long) -> Unit,
    onNavigateToLine: (lineId: Long) -> Unit,
    onNavigateToLines: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSetNotification: (stopId: Long, stopName: String, lineId: Long, lineName: String, lineColor: String, direction: Direction, minutes: Int, isTimetable: Boolean) -> Unit
) {
    val widgets by viewModel.widgets.collectAsState()
    val cardStates by viewModel.cardStates.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val hiddenGroups by viewModel.hiddenGroups.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastRefreshMs by viewModel.lastRefreshMs.collectAsState()
    val lineTimetables by viewModel.lineTimetables.collectAsState()
    val timetableLoading by viewModel.timetableLoading.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    // Only poll while this screen is on screen
    DisposableEffect(viewModel) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    // Wall clock used to tick countdowns and "updated Ns ago" between fetches
    var now by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            now = currentTimeMillis()
        }
    }

    // Notification dialog state
    data class BellTarget(val stopId: Long, val stopName: String, val lineId: Long, val lineName: String, val lineColor: String, val direction: Direction)
    var bellTarget by remember { mutableStateOf<BellTarget?>(null) }

    // Group widgets
    val grouped = remember(widgets, groups) {
        val map = mutableMapOf<String, MutableList<WidgetConfig>>()
        widgets.forEach { w ->
            map.getOrPut(w.group) { mutableListOf() }.add(w)
        }
        val order = groups + ""
        map.entries.sortedBy { entry ->
            val idx = order.indexOf(entry.key)
            if (idx == -1) order.size else idx
        }.map { it.key to it.value.toList() }
    }
    val hasGroups = grouped.size > 1 || (grouped.size == 1 && grouped[0].first.isNotEmpty())

    // Count how many widgets are actually visible
    val visibleCount = widgets.count { w ->
        !w.hidden && w.group !in hiddenGroups
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        DashboardHeader(
            widgetCount = widgets.size,
            isEditing = isEditing,
            isRefreshing = isRefreshing,
            lastRefreshMs = lastRefreshMs,
            now = now,
            locale = locale,
            onToggleEditing = { viewModel.toggleEditing() },
            onReset = { showResetDialog = true },
            onRefresh = { viewModel.refreshNow() },
            onAddStation = onNavigateToLines,
            onSettings = onNavigateToSettings
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshNow() },
            modifier = Modifier.weight(1f)
        ) {
            if (widgets.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Home,
                    title = t("dashboard.emptyTitle", locale),
                    description = t("dashboard.emptyDescription", locale),
                    modifier = Modifier.fillMaxSize()
                ) {
                    FilledTonalButton(onClick = onNavigateToLines) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("widget.addStation", locale))
                    }
                }
            } else if (!isEditing && visibleCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("dashboard.allHidden", locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalMuted.current,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (groupName, items) ->
                        val isGroupHidden = groupName in hiddenGroups

                        if (isGroupHidden && !isEditing) return@forEach

                        if (hasGroups) {
                            item(
                                key = "group_header_$groupName",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .alpha(if (isEditing && isGroupHidden) 0.4f else 1f)
                                ) {
                                    Text(
                                        text = groupName.ifEmpty { t("widget.noGroup", locale) }.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LocalMuted.current,
                                        letterSpacing = 1.sp
                                    )
                                    if (isEditing) {
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { viewModel.toggleGroupHidden(groupName) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                if (isGroupHidden) Icons.Default.Lock
                                                else Icons.Default.Check,
                                                contentDescription = null,
                                                tint = LocalMuted.current,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(
                            items.filter { !it.hidden || isEditing },
                            key = { it.id },
                            span = { widget ->
                                // Large cards take two columns when the grid has them
                                if (widget.size == WidgetSize.LARGE) GridItemSpan(minOf(2, maxLineSpan))
                                else GridItemSpan(1)
                            }
                        ) { widget ->
                            StationWidgetCard(
                                widget = widget,
                                cardState = cardStates[widget.stopId],
                                now = now,
                                isEditing = isEditing,
                                groups = groups,
                                lineTimetables = lineTimetables,
                                timetableLoading = timetableLoading,
                                locale = locale,
                                onDelete = { viewModel.removeWidget(widget.id) },
                                onToggleHidden = { viewModel.toggleWidgetHidden(widget.id) },
                                onToggleLineVisibility = { lineKey ->
                                    viewModel.toggleLineVisibility(widget.id, lineKey)
                                },
                                onSetGroup = { group -> viewModel.setWidgetGroup(widget.id, group) },
                                onAddGroup = { name -> viewModel.addGroup(name) },
                                onResize = { size -> viewModel.resizeWidget(widget.id, size) },
                                onClick = { onNavigateToStop(widget.stopId) },
                                onLineClick = { lineId -> onNavigateToLine(lineId) },
                                onLoadTimetable = { lineId, direction ->
                                    viewModel.loadLineTimetable(widget.stopId, lineId, direction)
                                },
                                onBellClick = { lineId, lineName, lineColor, direction, minutes, isTimetable ->
                                    if (useDirectNotificationAction) {
                                        onSetNotification(
                                            widget.stopId,
                                            widget.stopName,
                                            lineId,
                                            lineName,
                                            lineColor,
                                            direction,
                                            minutes,
                                            isTimetable
                                        )
                                    } else {
                                        bellTarget = BellTarget(widget.stopId, widget.stopName, lineId, lineName, lineColor, direction)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(t("dashboard.resetConfirmTitle", locale)) },
            text = { Text(t("dashboard.resetConfirmDescription", locale)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showResetDialog = false
                }) {
                    Text(t("dashboard.resetConfirm", locale))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(t("dashboard.cancel", locale))
                }
            }
        )
    }

    if (!useDirectNotificationAction) bellTarget?.let { target ->
        NotificationDialog(
            lineName = target.lineName,
            lineColor = target.lineColor,
            stopName = target.stopName,
            locale = locale,
            onConfirm = { mode, thresholdMinutes ->
                notificationStore.addSubscription(
                    stopId = target.stopId,
                    stopName = target.stopName,
                    lineId = target.lineId,
                    lineName = target.lineName,
                    lineColor = target.lineColor,
                    direction = target.direction,
                    mode = mode,
                    thresholdMinutes = thresholdMinutes
                )
                bellTarget = null
            },
            onDismiss = { bellTarget = null }
        )
    }
}

@Composable
private fun DashboardHeader(
    widgetCount: Int,
    isEditing: Boolean,
    isRefreshing: Boolean,
    lastRefreshMs: Long,
    now: Long,
    locale: Locale,
    onToggleEditing: () -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
    onAddStation: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("dashboard.title", locale),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (widgetCount > 0) {
                Text(
                    text = when {
                        isRefreshing -> t("dashboard.updating", locale)
                        lastRefreshMs == 0L -> ""
                        else -> "${t("dashboard.updated", locale)} ${formatAgo(now - lastRefreshMs, locale)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current
                )
            }
        }

        if (isEditing) {
            OutlinedButton(onClick = onReset) {
                Text(t("dashboard.reset", locale), fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onToggleEditing) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("dashboard.done", locale), fontSize = 13.sp)
            }
        } else {
            IconButton(onClick = onAddStation) {
                Icon(Icons.Default.Add, contentDescription = t("widget.addStation", locale))
            }
            if (widgetCount > 0) {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = t("dashboard.refresh", locale))
                    }
                }
                IconButton(onClick = onToggleEditing) {
                    Icon(Icons.Default.Edit, contentDescription = t("dashboard.edit", locale))
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = t("nav.settings", locale))
            }
        }
    }
}

/** "just now", "12s ago", "3 min ago" in the active locale. */
private fun formatAgo(elapsedMs: Long, locale: Locale): String {
    val seconds = (elapsedMs / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> t("time.justNow", locale)
        seconds < 60 -> t("time.secondsAgo", locale).replace("{n}", seconds.toString())
        else -> t("time.minutesAgo", locale).replace("{n}", (seconds / 60).toString())
    }
}

/** Data older than this is flagged on the card. */
private const val STALE_AFTER_MS = 60_000L

@Composable
private fun StationWidgetCard(
    widget: WidgetConfig,
    cardState: StopCardState?,
    now: Long,
    isEditing: Boolean,
    groups: List<String>,
    lineTimetables: Map<String, List<TimetableHour>>,
    timetableLoading: Set<String>,
    locale: Locale,
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleLineVisibility: (String) -> Unit,
    onSetGroup: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onResize: (WidgetSize) -> Unit,
    onClick: () -> Unit,
    onLineClick: (lineId: Long) -> Unit,
    onLoadTimetable: (lineId: Long, direction: Direction) -> Unit,
    onBellClick: (lineId: Long, lineName: String, lineColor: String, direction: Direction, minutes: Int, isTimetable: Boolean) -> Unit
) {
    val stopInfo = cardState?.info
    val allLines = stopInfo?.lines ?: emptyList()
    val visibleLines = widget.visibleLines

    val sortedLines = remember(allLines, visibleLines) {
        allLines
            .filter { line ->
                visibleLines.isEmpty() || visibleLines.contains("${line.id}_${line.direction.value}")
            }
            .sortedWith(
                compareBy<LineArrivalDetail> {
                    if (it.arrivingTime >= 0) 0 else 1
                }.thenBy { it.arrivingTime }
            )
    }

    // Seconds since this card's data was fetched; drives the local countdown
    val elapsedSec = if (cardState != null && cardState.lastUpdatedMs > 0) {
        ((now - cardState.lastUpdatedMs) / 1000).coerceAtLeast(0)
    } else 0L
    val isStale = cardState != null && cardState.hasData &&
        (cardState.error != null || now - cardState.lastUpdatedMs > STALE_AFTER_MS)

    val maxLines = if (widget.size == WidgetSize.LARGE) Int.MAX_VALUE else 4

    // Accent colour follows the dominant transport type at this stop
    val accent = remember(allLines) {
        allLines.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
    }?.let { transportColor(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .alpha(if (isEditing && widget.hidden) 0.4f else 1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent strip
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )

            Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                // Hero header: station name + status + menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onClick)
                    ) {
                        Text(
                            text = widget.stopName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (widget.group.isNotEmpty()) {
                                Text(
                                    text = widget.group,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalMuted.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (isStale) {
                                StaleChip(locale = locale)
                            } else if (cardState?.isLoading == true && cardState.hasData) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                            }
                        }
                    }

                    if (isEditing) {
                        Row {
                            IconButton(
                                onClick = onToggleHidden,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (widget.hidden) Icons.Default.Lock
                                    else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = LocalMuted.current,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = t("widget.delete", locale),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        WidgetDropdownMenu(
                            widget = widget,
                            allLines = allLines,
                            groups = groups,
                            locale = locale,
                            onToggleLineVisibility = onToggleLineVisibility,
                            onSetGroup = onSetGroup,
                            onAddGroup = onAddGroup,
                            onResize = onResize,
                            onDelete = onDelete
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Line rows
                if (stopInfo != null && sortedLines.isNotEmpty()) {
                    Column {
                        sortedLines.take(maxLines).forEachIndexed { index, line ->
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            val timetableKey = "${widget.stopId}_${line.id}_${line.direction.value}"
                            WidgetLineRow(
                                line = line,
                                elapsedSec = elapsedSec,
                                timetable = lineTimetables[timetableKey],
                                timetableLoading = timetableKey in timetableLoading,
                                locale = locale,
                                onLineClick = { onLineClick(line.id) },
                                onBellClick = {
                                    onBellClick(
                                        line.id,
                                        line.name,
                                        line.color,
                                        line.direction,
                                        ((line.arrivingTime - elapsedSec) / 60).toInt().coerceAtLeast(0),
                                        line.isTimetable
                                    )
                                },
                                onLoadTimetable = { onLoadTimetable(line.id, line.direction) }
                            )
                        }
                    }

                    if (sortedLines.size > maxLines) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onClick)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${sortedLines.size - maxLines} ${t("widget.moreLines", locale)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (stopInfo == null && cardState?.error == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (stopInfo == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = t("widget.loadFailed", locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMuted.current
                        )
                    }
                } else {
                    Text(
                        text = t("widget.noData", locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current
                    )
                }
            }
        }
    }
}

@Composable
private fun StaleChip(locale: Locale) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = t("dashboard.stale", locale),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun WidgetLineRow(
    line: LineArrivalDetail,
    elapsedSec: Long,
    timetable: List<TimetableHour>?,
    timetableLoading: Boolean,
    locale: Locale,
    onLineClick: () -> Unit,
    onBellClick: () -> Unit,
    onLoadTimetable: () -> Unit
) {
    val upcomingCount = 2
    var showTimetable by remember { mutableStateOf(false) }
    val inService = line.arrivingTime >= 0
    // Countdown keeps moving between fetches
    val liveSeconds = (line.arrivingTime - elapsedSec).coerceAtLeast(0)
    val isImminent = inService && liveSeconds < 120

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable(onClick = onLineClick)) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Following arrivals, inline under the direction
                if (inService && line.arrivingTimes.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${t("widget.nextArrivals", locale)}:",
                            fontSize = 10.sp,
                            color = LocalMuted.current
                        )
                        line.arrivingTimes.drop(1).take(upcomingCount).forEach { arrival ->
                            ArrivalTimeBadge(
                                seconds = (arrival.arrivingTime - elapsedSec).coerceAtLeast(0),
                                isTimetable = arrival.isTimetable,
                                size = BadgeSize.SMALL
                            )
                        }
                    }
                }
            }
            if (inService) {
                IconButton(
                    onClick = {
                        if (!showTimetable) {
                            onLoadTimetable()
                        }
                        showTimetable = !showTimetable
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (timetableLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = if (showTimetable) MaterialTheme.colorScheme.primary else LocalMuted.current,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onBellClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = LocalMuted.current,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                // The hero number: big, and highlighted when the vehicle is about to arrive
                Box(
                    modifier = if (isImminent) Modifier
                        .border(1.5.dp, RealtimeColor, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                    else Modifier
                ) {
                    ArrivalTimeBadge(
                        seconds = liveSeconds,
                        isTimetable = line.isTimetable,
                        size = BadgeSize.LARGE
                    )
                }
            } else {
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

        // Inline timetable (next 3 hours)
        if (showTimetable && timetable != null && timetable.isNotEmpty()) {
            val currentHour = currentHourOfDay()
            val currentMinute = currentMinuteOfHour()
            val filteredEntries = timetable.filter { entry ->
                val h = entry.hour.toIntOrNull() ?: return@filter false
                h in currentHour..(currentHour + 2)
            }.map { entry ->
                val h = entry.hour.toIntOrNull() ?: 0
                if (h == currentHour) {
                    entry.copy(minutes = entry.minutes.filter { m ->
                        (m.toIntOrNull() ?: 0) >= currentMinute
                    })
                } else entry
            }.filter { it.minutes.isNotEmpty() }

            Spacer(Modifier.height(4.dp))
            if (filteredEntries.isEmpty()) {
                Text(
                    text = t("widget.noScheduled", locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    modifier = Modifier.padding(start = 34.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(start = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    filteredEntries.forEach { entry ->
                        val h = entry.hour.toIntOrNull() ?: 0
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = entry.hour.padStart(2, '0'),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                entry.minutes.forEach { minute ->
                                    val minuteVal = minute.toIntOrNull() ?: 0
                                    val highlight = h == currentHour && minuteVal >= currentMinute &&
                                            entry.minutes.none { m ->
                                                val mv = m.toIntOrNull() ?: 0
                                                mv in currentMinute until minuteVal
                                            }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .then(
                                                if (highlight) Modifier.border(
                                                    1.5.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(4.dp)
                                                ) else Modifier
                                            )
                                            .background(
                                                if (highlight) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = minute.padStart(2, '0'),
                                            fontSize = 11.sp,
                                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                                            color = if (highlight) MaterialTheme.colorScheme.primary
                                            else LocalMuted.current
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
private fun WidgetDropdownMenu(
    widget: WidgetConfig,
    allLines: List<LineArrivalDetail>,
    groups: List<String>,
    locale: Locale,
    onToggleLineVisibility: (String) -> Unit,
    onSetGroup: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onResize: (WidgetSize) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = LocalMuted.current,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Filter lines
            if (allLines.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("widget.filterLines", locale), fontSize = 13.sp)
                            if (widget.visibleLines.isNotEmpty()) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${widget.visibleLines.size}/${allLines.size}",
                                    fontSize = 11.sp,
                                    color = LocalMuted.current
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        showFilterMenu = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            // Size
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("widget.size", locale), fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (widget.size == WidgetSize.LARGE) t("widget.sizeLarge", locale)
                            else t("widget.sizeMedium", locale),
                            fontSize = 11.sp,
                            color = LocalMuted.current
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onResize(if (widget.size == WidgetSize.LARGE) WidgetSize.MEDIUM else WidgetSize.LARGE)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            // Group
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("widget.group", locale), fontSize = 13.sp)
                        if (widget.group.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                widget.group,
                                fontSize = 11.sp,
                                color = LocalMuted.current
                            )
                        }
                    }
                },
                onClick = {
                    expanded = false
                    showGroupMenu = true
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Edit, // Tag icon
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            // Delete
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        t("widget.delete", locale),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    onDelete()
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        // Filter lines sub-menu
        DropdownMenu(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false }
        ) {
            allLines.forEach { line ->
                val lineKey = "${line.id}_${line.direction.value}"
                val isVisible = widget.visibleLines.isEmpty() || widget.visibleLines.contains(lineKey)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(
                                name = line.name,
                                color = line.color,
                                size = BadgeSize.SMALL
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                line.directionName,
                                fontSize = 11.sp,
                                color = LocalMuted.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    },
                    onClick = { onToggleLineVisibility(lineKey) },
                    leadingIcon = {
                        Icon(
                            if (isVisible) Icons.Default.Check else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isVisible) MaterialTheme.colorScheme.onSurface else LocalMuted.current,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = if (!isVisible) Modifier.alpha(0.5f) else Modifier
                )
            }
        }

        // Group sub-menu
        DropdownMenu(
            expanded = showGroupMenu,
            onDismissRequest = { showGroupMenu = false }
        ) {
            // No group option
            DropdownMenuItem(
                text = { Text(t("widget.noGroup", locale), fontSize = 13.sp) },
                onClick = {
                    onSetGroup("")
                    showGroupMenu = false
                },
                trailingIcon = {
                    if (widget.group.isEmpty()) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
            if (groups.isNotEmpty()) {
                HorizontalDivider()
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group, fontSize = 13.sp) },
                        onClick = {
                            onSetGroup(group)
                            showGroupMenu = false
                        },
                        trailingIcon = {
                            if (widget.group == group) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
            // Add new group inline
            HorizontalDivider()
            var newGroupName by remember { mutableStateOf("") }
            DropdownMenuItem(
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = {
                            Text(t("widget.addGroup", locale), fontSize = 12.sp)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                onClick = {
                    val trimmed = newGroupName.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddGroup(trimmed)
                        onSetGroup(trimmed)
                        newGroupName = ""
                        showGroupMenu = false
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}
