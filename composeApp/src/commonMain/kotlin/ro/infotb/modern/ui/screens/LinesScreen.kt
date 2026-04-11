package ro.infotb.modern.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ro.infotb.modern.data.model.Line
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.ui.components.shared.EmptyState
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.transportColor
import ro.infotb.modern.ui.viewmodel.LinesViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@OptIn(ExperimentalLayoutApi::class)
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
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(t("lines.searchPlaceholder", locale)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Transport type filters
        var showNightOnly by remember { mutableStateOf(false) }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.2f),
                        selectedLabelColor = color
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

        Spacer(Modifier.height(12.dp))

        // Content
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
                    modifier = Modifier.weight(1f)
                ) {
                    TextButton(onClick = { viewModel.loadLines() }) {
                        Text("Retry")
                    }
                }
            }

            filteredLines.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = t("lines.emptyTitle", locale),
                    description = t("lines.emptyDescription", locale),
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).animateContentSize(),
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
                                onClick = { onNavigateToLine(line.id) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        item(key = "fav_divider") {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    fun isNightLine(line: Line) = line.name.startsWith("N", ignoreCase = true)

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
                                onClick = { onNavigateToLine(line.id) }
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
                            val typeIcon = when (type) {
                                TransportType.BUS -> "\uD83D\uDE8C"
                                TransportType.TRAM -> "\uD83D\uDE8B"
                                TransportType.CABLE_CAR -> "\uD83D\uDE8E"
                                TransportType.SUBWAY -> "\uD83D\uDE87"
                            }

                            item(key = "header_${type.name}") {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = typeIcon, fontSize = 16.sp)
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
                                }
                            }
                            items(linesOfType, key = { "line_${it.id}" }) { line ->
                                LineRow(
                                    line = line,
                                    isFavorite = line.id in favoriteIds,
                                    onToggleFavorite = { viewModel.toggleFavorite(line.id) },
                                    onClick = { onNavigateToLine(line.id) }
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(
    line: Line,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineBadge(
                name = line.name,
                color = line.color,
                size = BadgeSize.MEDIUM
            )
            Spacer(Modifier.weight(1f))
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
