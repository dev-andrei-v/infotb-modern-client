package ro.infotb.modern.ui.components.shared

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

private val ONCE_OPTIONS = listOf(1, 3, 5, 10)
private val REPEAT_OPTIONS = listOf(1, 2, 3, 5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationDialog(
    lineName: String,
    lineColor: String,
    stopName: String,
    locale: Locale,
    onConfirm: (mode: NotificationMode, thresholdMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(NotificationMode.ONCE) }
    var threshold by remember { mutableStateOf(3) }

    val options = if (mode == NotificationMode.ONCE) ONCE_OPTIONS else REPEAT_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = { Text(t("notification.setup", locale)) },
        text = {
            Column {
                // Line info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LineBadge(name = lineName, color = lineColor, size = BadgeSize.SMALL)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stopName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Mode selection
                Text(
                    text = t("notification.mode", locale).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMuted.current,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = t("notification.modeOnce", locale),
                        selected = mode == NotificationMode.ONCE,
                        onClick = { mode = NotificationMode.ONCE; threshold = 3 },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = t("notification.modeRepeat", locale),
                        selected = mode == NotificationMode.REPEAT,
                        onClick = { mode = NotificationMode.REPEAT; threshold = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Threshold selection
                Text(
                    text = (if (mode == NotificationMode.ONCE) t("notification.notifyWhen", locale)
                    else t("notification.repeatEvery", locale)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMuted.current,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { min ->
                        val selected = threshold == min
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                                    else Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                )
                                .clickable { threshold = min }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$min min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = if (mode == NotificationMode.ONCE)
                        t("notification.onceDescription", locale).replace("{min}", "$threshold")
                    else t("notification.repeatDescription", locale).replace("{min}", "$threshold"),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(mode, threshold) }) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(t("notification.activate", locale))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("dashboard.cancel", locale))
            }
        }
    )
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                else Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else LocalMuted.current
        )
    }
}
