package ro.infotb.modern.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ro.infotb.modern.data.model.Line
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.ui.components.shared.EmptyState
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.vehicleIcon
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.ui.viewmodel.LinesViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@Composable
fun LinesScreen(
    viewModel: LinesViewModel,
    locale: Locale,
    onNavigateToLine: (lineId: Long) -> Unit
) {
    val filteredLines by viewModel.filteredLines.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchingStops by viewModel.isSearchingStops.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val lineDisplayStyle by viewModel.lineDisplayStyle.collectAsState()
    var showDisplayMenu by remember { mutableStateOf(false) }

    var showNightOnly by remember { mutableStateOf(false) }
    var collapsedTypes by remember { mutableStateOf(setOf<TransportType>()) }
    var collapsedTypesBeforeSearch by remember { mutableStateOf<Set<TransportType>?>(null) }

    // Auto-expand sections when searching, restore when cleared (matching Swift LinesModel behavior)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            if (collapsedTypesBeforeSearch == null) {
                collapsedTypesBeforeSearch = collapsedTypes
            }
            collapsedTypes = emptySet()
        } else {
            collapsedTypesBeforeSearch?.let {
                collapsedTypes = it
                collapsedTypesBeforeSearch = null
            }
        }
    }

    fun isNightLine(line: Line) = line.name.startsWith("N", ignoreCase = true)

    val expandableTypes = remember(filteredLines, showNightOnly) {
        if (showNightOnly) emptyList()
        else listOf(TransportType.BUS, TransportType.TRAM, TransportType.CABLE_CAR, TransportType.SUBWAY)
            .filter { type -> filteredLines.any { !isNightLine(it) && it.type == type } }
    }
    val allSectionsExpanded = expandableTypes.isNotEmpty() && expandableTypes.none { it in collapsedTypes }

    fun toggleAllSections() {
        if (expandableTypes.isEmpty()) return
        collapsedTypes = if (allSectionsExpanded) {
            collapsedTypes + expandableTypes
        } else {
            collapsedTypes - expandableTypes.toSet()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("lines.title", locale),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("lines.description", locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current
                )
            }

            if (!showNightOnly && expandableTypes.isNotEmpty()) {
                IconButton(
                    onClick = { toggleAllSections() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (allSectionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = t(if (allSectionsExpanded) "lines.collapseAll" else "lines.expandAll", locale),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box {
                TextButton(onClick = { showDisplayMenu = true }) {
                    Text(t(if (lineDisplayStyle == "bus_display") "lines.styleBusDisplay" else "lines.styleNormal", locale), maxLines = 1)
                }
                DropdownMenu(expanded = showDisplayMenu, onDismissRequest = { showDisplayMenu = false }) {
                    DropdownMenuItem(text = { Text(t("lines.styleNormal", locale)) }, onClick = { viewModel.setLineDisplayStyle("normal"); showDisplayMenu = false })
                    DropdownMenuItem(text = { Text(t("lines.styleBusDisplay", locale)) }, onClick = { viewModel.setLineDisplayStyle("bus_display"); showDisplayMenu = false })
                }
            }
        }

        // Main List Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            // Skeleton placeholders
                            repeat(6) {
                                SkeletonLineRow()
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                error != null -> {
                    EmptyState(
                        icon = Icons.Default.Warning,
                        title = t("lines.errorTitle", locale),
                        description = t("lines.errorDescription", locale),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TextButton(onClick = { viewModel.loadLines() }) {
                            Text(t("common.retry", locale))
                        }
                    }
                }

                filteredLines.isEmpty() && isSearchingStops -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(t("lines.searchingStops", locale), color = LocalMuted.current)
                        }
                    }
                }

                filteredLines.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = t("lines.emptyTitle", locale),
                        description = t("lines.emptyDescription", locale),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().animateContentSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        )
                    ) {
                        // Favorites section
                        val favorites = filteredLines.filter { it.id in favoriteIds }
                        if (favorites.isNotEmpty()) {
                            item(key = "fav_header") {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = t("lines.favorites", locale),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            items(favorites, key = { "fav_${it.id}" }) { line ->
                                LineRow(
                                    line = line,
                                    isFavorite = true,
                                    onToggleFavorite = { viewModel.toggleFavorite(line.id) },
                                    onClick = { onNavigateToLine(line.id) },
                                    onLoadSummary = { viewModel.loadLineSummary(line.id) }
                                    ,busDisplay = lineDisplayStyle == "bus_display"
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            item(key = "fav_divider") {
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        fun sortLines(lines: List<Line>): List<Line> {
                            return lines.sortedWith(
                                compareBy<Line> {
                                    it.name.filter { c -> c.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
                                }.thenBy { it.name }
                            )
                        }

                        val nightColor = Color(0xFF5C6BC0)

                        if (showNightOnly) {
                            // Night chip active — show only night lines
                            val nightLines = sortLines(filteredLines.filter { isNightLine(it) })
                            item(key = "header_night") {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = t("lines.night", locale),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = nightColor
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(${nightLines.size})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalMuted.current
                                    )
                                }
                            }
                            items(nightLines, key = { "line_night_${it.id}" }) { line ->
                                LineRow(
                                    line = line,
                                    isFavorite = line.id in favoriteIds,
                                    onToggleFavorite = { viewModel.toggleFavorite(line.id) },
                                    onClick = { onNavigateToLine(line.id) },
                                    onLoadSummary = { viewModel.loadLineSummary(line.id) }
                                    ,busDisplay = lineDisplayStyle == "bus_display"
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        } else {
                            // Day lines grouped by transport type
                            val dayLines = filteredLines.filter { !isNightLine(it) }
                            val grouped = dayLines.groupBy { it.type }
                            val typeOrder = listOf(
                                TransportType.BUS,
                                TransportType.TRAM,
                                TransportType.CABLE_CAR,
                                TransportType.SUBWAY
                            )

                            typeOrder.forEach { type ->
                                val linesOfType = sortLines(grouped[type] ?: return@forEach)
                                val collapsed = type in collapsedTypes

                                // Section header: tap anywhere on it to fold or unfold the section
                                item(key = "header_${type.name}") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                collapsedTypes =
                                                    if (collapsed) collapsedTypes - type else collapsedTypes + type
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = vehicleIcon(type),
                                            contentDescription = null,
                                            tint = transportColor(type),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = t("transport.${type.name}", locale),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = transportColor(type)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "(${linesOfType.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalMuted.current
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Icon(
                                            if (collapsed) Icons.Default.KeyboardArrowRight
                                            else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = LocalMuted.current,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (collapsed) return@forEach
                                items(linesOfType, key = { "line_${it.id}" }) { line ->
                                    LineRow(
                                        line = line,
                                        isFavorite = line.id in favoriteIds,
                                        onToggleFavorite = { viewModel.toggleFavorite(line.id) },
                                        onClick = { onNavigateToLine(line.id) },
                                        onLoadSummary = { viewModel.loadLineSummary(line.id) }
                                        ,busDisplay = lineDisplayStyle == "bus_display"
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Docked Bottom Bar (Filters + Search within thumb reach, matching Swift)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Transport type filters row (horizontal scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportType.entries.forEach { type ->
                        val isSelected = type in selectedTypes
                        val color = transportColor(type)
                        val label = t("transport.${type.name}", locale)

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (showNightOnly) showNightOnly = false
                                viewModel.toggleTypeFilter(type)
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = vehicleIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor = color,
                                selectedLeadingIconColor = color
                            )
                        )
                    }
                    // Night filter chip
                    FilterChip(
                        selected = showNightOnly,
                        onClick = { showNightOnly = !showNightOnly },
                        label = { Text(t("lines.night", locale), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF5C6BC0).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF5C6BC0)
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Search field docked at bottom
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(t("lines.searchPlaceholder", locale), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = t("common.clear", locale),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

@Composable
private fun LineRow(
    line: Line,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLoadSummary: () -> Unit,
    busDisplay: Boolean
) {
    LaunchedEffect(line.id) { onLoadSummary() }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = if (busDisplay) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!busDisplay) {
                LineBadge(
                    name = if (line.type == TransportType.SUBWAY) line.name.substringBefore('-').trim() else line.name,
                    color = line.color,
                    size = BadgeSize.MEDIUM
                )
                Spacer(Modifier.width(12.dp))
            }
            val endpoints = listOfNotNull(line.startStopName, line.endStopName)
            if (busDisplay) {
                val lcdText = if (isSystemInDarkTheme()) Color(0xFFFFC857) else Color(0xFF9A5B00)
                Row(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (line.type == TransportType.SUBWAY) line.name.substringBefore('-').trim() else line.name,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        letterSpacing = 0.8.sp,
                        color = lcdText,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 64.dp)
                    )
                    Text(
                        text = endpoints.joinToString("\n") { it.uppercase() }.ifBlank { "—" },
                        modifier = Modifier.weight(1f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.6.sp,
                        color = lcdText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (endpoints.isNotEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF18212B)).padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = endpoints.joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFFFC107) else LocalMuted.current.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SkeletonLineRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )
}
