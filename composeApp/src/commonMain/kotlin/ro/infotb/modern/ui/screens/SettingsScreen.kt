package ro.infotb.modern.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import ro.infotb.modern.data.backup.BackupError
import ro.infotb.modern.data.backup.BackupManager
import ro.infotb.modern.data.backup.BackupSummary
import ro.infotb.modern.data.backup.RestoreMode
import ro.infotb.modern.data.backup.formatBackupDate
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.NotificationSubscription
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.ui.components.shared.BadgeSize
import ro.infotb.modern.ui.components.shared.LineBadge
import ro.infotb.modern.supportsDynamicIsland
import ro.infotb.modern.supportsLiveNotification
import ro.infotb.modern.ui.theme.LocalMuted
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

@Composable
fun SettingsScreen(
    preferencesStore: PreferencesStore,
    notificationStore: NotificationStore,
    backupManager: BackupManager,
    locale: Locale,
    onNotificationChanged: (() -> Unit)? = null
) {
    val theme by preferencesStore.theme.collectAsState()
    val subscriptions by notificationStore.subscriptions.collectAsState()
    val isDynamicIsland = supportsDynamicIsland()
    val isLiveNotification = supportsLiveNotification()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = t("settings.title", locale),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = t("settings.description", locale),
                style = MaterialTheme.typography.bodySmall,
                color = LocalMuted.current
            )
        }

        // Theme section
        SettingsSection(
            title = t("settings.theme", locale),
            description = t("settings.themeDescription", locale)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOption(
                    icon = "\u2600\uFE0F", // Sun
                    label = t("theme.light", locale),
                    isSelected = theme == "light",
                    onClick = { preferencesStore.setTheme("light") },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = "\uD83C\uDF19", // Moon
                    label = t("theme.dark", locale),
                    isSelected = theme == "dark",
                    onClick = { preferencesStore.setTheme("dark") },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = "\uD83D\uDDA5\uFE0F", // Monitor
                    label = t("theme.system", locale),
                    isSelected = theme == "system",
                    onClick = { preferencesStore.setTheme("system") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Language section
        SettingsSection(
            title = t("settings.language", locale),
            description = t("settings.languageDescription", locale)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageOption(
                    flag = "\uD83C\uDDF7\uD83C\uDDF4",
                    label = "Romana",
                    isSelected = locale == Locale.RO,
                    onClick = { preferencesStore.setLocale("ro") },
                    modifier = Modifier.weight(1f)
                )
                LanguageOption(
                    flag = "\uD83C\uDDEC\uD83C\uDDE7",
                    label = "English",
                    isSelected = locale == Locale.EN,
                    onClick = { preferencesStore.setLocale("en") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Notifications / Dynamic Island section
        SettingsSection(
            title = when {
                isDynamicIsland -> t("dynamicIsland.title", locale)
                isLiveNotification -> t("liveTracking.title", locale)
                else -> t("notifications.title", locale)
            },
            description = if (subscriptions.isNotEmpty()) when {
                isDynamicIsland -> t("dynamicIsland.enabled", locale)
                isLiveNotification -> t("liveTracking.enabled", locale)
                else -> "${subscriptions.size} ${t("notifications.active", locale)}"
            } else ""
        ) {
            if (subscriptions.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = LocalMuted.current,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isDynamicIsland -> t("dynamicIsland.emptyDescription", locale)
                            isLiveNotification -> t("liveTracking.emptyDescription", locale)
                            else -> t("notifications.emptyDescription", locale)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subscriptions.forEach { subscription ->
                        SettingsNotificationCard(
                            subscription = subscription,
                            locale = locale,
                            onToggle = {
                                notificationStore.toggleSubscription(subscription.id)
                                onNotificationChanged?.invoke()
                            },
                            onDelete = {
                                notificationStore.removeSubscription(subscription.id)
                                onNotificationChanged?.invoke()
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Backup & restore section
        SettingsSection(
            title = t("settings.backup", locale),
            description = t("settings.backupDescription", locale)
        ) {
            BackupSection(
                backupManager = backupManager,
                locale = locale,
                onRestored = { onNotificationChanged?.invoke() }
            )
        }

        Spacer(Modifier.height(16.dp))

        // About section
        SettingsSection(
            title = t("settings.about", locale),
            description = ""
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "InfoTB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t("settings.aboutDescription", locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMuted.current
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t("settings.aboutInfo", locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMuted.current,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun ThemeOption(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
            Spacer(Modifier.height(2.dp))
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Spacer(Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun LanguageOption(
    flag: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = flag, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
            if (isSelected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsNotificationCard(
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineBadge(
                    name = subscription.lineName,
                    color = subscription.lineColor,
                    size = BadgeSize.SMALL
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.stopName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (subscription.direction == Direction.TUR)
                            t("direction.tur", locale)
                        else t("direction.retur", locale),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = LocalMuted.current
                    )
                }
                Switch(
                    checked = subscription.enabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** A backup file waiting for the user to confirm how it should be applied. */
private data class PendingRestore(val text: String, val summary: BackupSummary)

@Composable
private fun BackupSection(
    backupManager: BackupManager,
    locale: Locale,
    onRestored: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }

    fun showStatus(message: String, isError: Boolean = false) {
        status = message
        statusIsError = isError
    }

    fun describe(error: Throwable): String = when (error) {
        is BackupError.UnsupportedVersion -> t("backup.unsupportedVersion", locale)
        is BackupError.Malformed -> t("backup.invalid", locale)
        else -> "${t("backup.failed", locale)}: ${error.message ?: ""}"
    }

    /** Validate the file's text and, if it is a backup, ask how to apply it. */
    fun offerRestore(text: String?) {
        if (text.isNullOrBlank()) {
            showStatus(t("backup.invalid", locale), isError = true)
            return
        }
        try {
            pending = PendingRestore(text, backupManager.inspect(text))
        } catch (e: Exception) {
            showStatus(describe(e), isError = true)
        }
    }

    val saver = rememberFileSaverLauncher { file ->
        isBusy = false
        showStatus(if (file != null) t("backup.exported", locale) else t("backup.cancelled", locale))
    }

    val picker = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("json")),
        title = t("backup.import", locale)
    ) { file ->
        if (file == null) {
            showStatus(t("backup.cancelled", locale))
            return@rememberFilePickerLauncher
        }
        scope.launch {
            isBusy = true
            try {
                offerRestore(file.readBytes().decodeToString())
            } catch (e: Exception) {
                showStatus(describe(e), isError = true)
            } finally {
                isBusy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        isBusy = true
                        try {
                            val json = backupManager.export()
                            saver.launch(
                                baseName = backupManager.suggestedBaseName(),
                                extension = "json",
                                bytes = json.encodeToByteArray()
                            )
                        } catch (e: Exception) {
                            isBusy = false
                            showStatus(describe(e), isError = true)
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("backup.export", locale), fontSize = 13.sp)
            }
            FilledTonalButton(
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                onClick = { picker.launch() }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("backup.import", locale), fontSize = 13.sp)
            }
        }
        status?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error else LocalMuted.current
            )
        }
    }

    pending?.let { restore ->
        var mode by remember { mutableStateOf(RestoreMode.REPLACE) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(t("backup.restoreTitle", locale)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val s = restore.summary
                    Text(
                        text = "${t("backup.exportedOn", locale)} ${formatBackupDate(s.exportedAt)} · ${s.platform}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMuted.current
                    )
                    Text(
                        text = "${s.widgets} ${t("backup.summaryWidgets", locale)} · " +
                            "${s.groups} ${t("backup.summaryGroups", locale)} · " +
                            "${s.favorites} ${t("backup.summaryFavorites", locale)} · " +
                            "${s.subscriptions} ${t("backup.summarySubscriptions", locale)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    RestoreModeOption(
                        selected = mode == RestoreMode.REPLACE,
                        title = t("backup.modeReplace", locale),
                        description = t("backup.modeReplaceDescription", locale),
                        onClick = { mode = RestoreMode.REPLACE }
                    )
                    RestoreModeOption(
                        selected = mode == RestoreMode.MERGE,
                        title = t("backup.modeMerge", locale),
                        description = t("backup.modeMergeDescription", locale),
                        onClick = { mode = RestoreMode.MERGE }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val chosen = mode
                        pending = null
                        scope.launch {
                            isBusy = true
                            try {
                                backupManager.restore(restore.text, chosen)
                                showStatus(t("backup.restored", locale))
                                onRestored()
                            } catch (e: Exception) {
                                showStatus(describe(e), isError = true)
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) {
                    Text(t("backup.restore", locale))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(t("backup.cancel", locale))
                }
            }
        )
    }
}

@Composable
private fun RestoreModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = LocalMuted.current)
        }
    }
}
