package ro.infotb.modern.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.NotificationSubscription
import ro.infotb.modern.supportsDynamicIsland
import ro.infotb.modern.supportsLiveNotification
import ro.infotb.modern.ui.components.shared.EmptyState
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@Composable
fun NotificationsScreen(
    notificationStore: NotificationStore,
    locale: Locale
) {
    val subscriptions by notificationStore.subscriptions.collectAsState()
    val isDynamicIsland = supportsDynamicIsland()
    val isLiveNotification = supportsLiveNotification()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = when {
                    isDynamicIsland -> t("dynamicIsland.title", locale)
                    isLiveNotification -> t("liveTracking.title", locale)
                    else -> t("notifications.title", locale)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (subscriptions.isNotEmpty()) {
                Text(
                    text = when {
                        isDynamicIsland -> t("dynamicIsland.enabled", locale)
                        isLiveNotification -> t("liveTracking.enabled", locale)
                        else -> "${subscriptions.size} ${t("notifications.active", locale)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current
                )
            }
        }

        if (subscriptions.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Notifications,
                title = when {
                    isDynamicIsland -> t("dynamicIsland.emptyTitle", locale)
                    isLiveNotification -> t("liveTracking.emptyTitle", locale)
                    else -> t("notifications.emptyTitle", locale)
                },
                description = when {
                    isDynamicIsland -> t("dynamicIsland.emptyDescription", locale)
                    isLiveNotification -> t("liveTracking.emptyDescription", locale)
                    else -> t("notifications.emptyDescription", locale)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(subscriptions, key = { it.id }) { subscription ->
                    NotificationCard(
                        subscription = subscription,
                        locale = locale,
                        onToggle = { notificationStore.toggleSubscription(subscription.id) },
                        onDelete = { notificationStore.removeSubscription(subscription.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    subscription: NotificationSubscription,
    locale: Locale,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val lineColor = try {
        parseColor(subscription.lineColor)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Colored top border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(lineColor)
            )

            Column(modifier = Modifier.padding(12.dp)) {
                // Line badge + stop name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LineBadge(
                        name = subscription.lineName,
                        color = subscription.lineColor,
                        size = BadgeSize.MEDIUM
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subscription.stopName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (subscription.direction == ro.infotb.modern.data.model.Direction.TUR)
                                t("direction.tur", locale)
                            else t("direction.retur", locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMuted.current
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Mode badge + threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode icon
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (subscription.mode == NotificationMode.ONCE) Icons.Default.Notifications
                                else Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = LocalMuted.current
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (subscription.mode == NotificationMode.ONCE)
                                    t("notification.modeOnce", locale)
                                else t("notification.modeRepeat", locale),
                                fontSize = 10.sp,
                                color = LocalMuted.current
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "${subscription.thresholdMinutes} ${t("settings.min", locale)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Toggle + delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = subscription.enabled,
                            onCheckedChange = { onToggle() }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (subscription.enabled)
                                t("notifications.disable", locale)
                            else t("notifications.enable", locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMuted.current
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
