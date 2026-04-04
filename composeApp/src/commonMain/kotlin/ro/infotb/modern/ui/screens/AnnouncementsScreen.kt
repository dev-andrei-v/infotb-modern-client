package ro.infotb.modern.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.model.Announcement
import ro.infotb.modern.ui.components.shared.EmptyState
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.viewmodel.AnnouncementsViewModel
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@Composable
fun AnnouncementsScreen(
    viewModel: AnnouncementsViewModel,
    locale: Locale
) {
    val announcements by viewModel.announcements.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = t("announcements.title", locale),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = t("announcements.description", locale),
                style = MaterialTheme.typography.bodySmall,
                color = LocalMuted.current
            )
        }

        when {
            isLoading && announcements.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && announcements.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Warning,
                    title = t("announcements.errorTitle", locale),
                    description = t("announcements.errorDescription", locale),
                    modifier = Modifier.weight(1f)
                ) {
                    TextButton(onClick = { viewModel.loadAnnouncements() }) {
                        Text("Retry")
                    }
                }
            }

            announcements.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Info,
                    title = t("announcements.emptyTitle", locale),
                    description = "",
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(announcements, key = { it.id }) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            locale = locale
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    locale: Locale
) {
    var expanded by remember { mutableStateOf(false) }
    val isLong = announcement.message.length > 200

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: icon + title + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF8F00),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatRelativeDate(announcement.created_at, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current,
                        fontSize = 11.sp
                    )
                }
            }

            // Line badges
            if (announcement.lines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    announcement.lines.forEach { line ->
                        LineBadge(
                            name = line.name,
                            color = line.color,
                            size = BadgeSize.SMALL
                        )
                    }
                }
            }

            // Message
            Spacer(Modifier.height(8.dp))
            val plainMessage = remember(announcement.message) { stripHtml(announcement.message) }
            Text(
                text = plainMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded || !isLong) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )

            if (isLong) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) t("announcements.showLess", locale)
                        else t("announcements.showMore", locale),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Formats a relative date from epoch seconds.
 * Without kotlinx-datetime in common, we use a compile-time approximate "now".
 * In production, add kotlinx-datetime for accurate wall-clock time.
 */
private fun stripHtml(html: String): String {
    return html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<p>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { mr ->
            mr.groupValues[1].toIntOrNull()?.let { code ->
                code.toChar().toString()
            } ?: mr.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { mr ->
            mr.groupValues[1].toIntOrNull(16)?.let { code ->
                code.toChar().toString()
            } ?: mr.value
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun formatRelativeDate(epochSeconds: Long, locale: Locale): String {
    val nowSeconds = ro.infotb.modern.currentTimeMillis() / 1000
    val diffSeconds = nowSeconds - epochSeconds
    val diffDays = if (diffSeconds > 0) diffSeconds / 86400 else 0

    return when {
        diffDays < 1 -> t("announcements.today", locale)
        diffDays < 2 -> t("announcements.yesterday", locale)
        diffDays < 7 -> "${diffDays}d"
        diffDays < 30 -> "${diffDays / 7}w"
        else -> "${diffDays / 30}mo"
    }
}
