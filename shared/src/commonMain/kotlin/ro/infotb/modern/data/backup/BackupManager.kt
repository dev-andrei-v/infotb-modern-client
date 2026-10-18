package ro.infotb.modern.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.db.FavoriteDao
import ro.infotb.modern.data.db.FavoriteEntity
import ro.infotb.modern.data.db.GroupDao
import ro.infotb.modern.data.db.GroupEntity
import ro.infotb.modern.data.db.NotificationDao
import ro.infotb.modern.data.db.NotificationEntity
import ro.infotb.modern.data.db.WidgetDao
import ro.infotb.modern.data.db.WidgetEntity
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.getPlatformName

const val BACKUP_FORMAT_VERSION = 1

/** Everything a user builds up in the app that is worth carrying to a new install. */
@Serializable
data class BackupPayload(
    val version: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Long,
    val platform: String,
    val preferences: BackupPreferences,
    val widgets: List<BackupWidget> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
    val favorites: List<Long> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList()
)

@Serializable
data class BackupPreferences(val theme: String, val locale: String)

@Serializable
data class BackupWidget(
    val id: String,
    val stopId: Long,
    val stopName: String,
    val size: String,
    val groupName: String = "",
    val visibleLines: List<String> = emptyList(),
    val hidden: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class BackupGroup(val name: String, val sortOrder: Int = 0, val hidden: Boolean = false)

@Serializable
data class BackupSubscription(
    val id: String,
    val stopId: Long,
    val stopName: String,
    val lineId: Long,
    val lineName: String,
    val lineColor: String,
    val direction: Int,
    val mode: String,
    val thresholdMinutes: Int,
    val enabled: Boolean = false
)

/** What a backup file contains, shown to the user before they confirm a restore. */
data class BackupSummary(
    val widgets: Int,
    val groups: Int,
    val favorites: Int,
    val subscriptions: Int,
    val exportedAt: Long,
    val platform: String
)

enum class RestoreMode {
    /** Wipe current data and load the backup as-is. */
    REPLACE,
    /** Keep current data and add what the backup has on top. */
    MERGE
}

sealed class BackupError(message: String) : Exception(message) {
    class Malformed(message: String) : BackupError(message)
    class UnsupportedVersion(val found: Int) : BackupError("Unsupported backup version $found")
}

class BackupManager(
    private val widgetDao: WidgetDao,
    private val groupDao: GroupDao,
    private val favoriteDao: FavoriteDao,
    private val notificationDao: NotificationDao,
    private val preferencesStore: PreferencesStore,
    private val json: Json
) {

    /** Serialises the current state to a JSON document. */
    suspend fun export(): String {
        val payload = BackupPayload(
            exportedAt = currentTimeMillis(),
            platform = getPlatformName(),
            preferences = BackupPreferences(
                theme = preferencesStore.theme.value,
                locale = preferencesStore.locale.value
            ),
            widgets = widgetDao.getAll().map { it.toBackup() },
            groups = groupDao.getAll().map { BackupGroup(it.name, it.sortOrder, it.hidden) },
            favorites = favoriteDao.getAllIds(),
            subscriptions = notificationDao.getAll().map { it.toBackup() }
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    /** Suggested file name for an export, e.g. `infotb-backup-2026-10-18.json`. */
    fun suggestedBaseName(): String = "infotb-backup-${formatBackupDate(currentTimeMillis())}"

    /** Parses and validates [text] without touching the database. */
    fun inspect(text: String): BackupSummary = parse(text).summary()

    /**
     * Applies [text] to the database. Returns the summary of what was applied.
     * Throws [BackupError] when the document is not a valid backup.
     */
    suspend fun restore(text: String, mode: RestoreMode): BackupSummary {
        val payload = parse(text)
        when (mode) {
            RestoreMode.REPLACE -> replace(payload)
            RestoreMode.MERGE -> merge(payload)
        }
        return payload.summary()
    }

    private fun parse(text: String): BackupPayload {
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupError.Malformed(e.message ?: "Not a valid backup file")
        } catch (e: IllegalArgumentException) {
            throw BackupError.Malformed(e.message ?: "Not a valid backup file")
        }
        if (payload.version > BACKUP_FORMAT_VERSION) {
            throw BackupError.UnsupportedVersion(payload.version)
        }
        return payload
    }

    private suspend fun replace(payload: BackupPayload) {
        widgetDao.deleteAll()
        groupDao.deleteAll()
        favoriteDao.deleteAll()
        notificationDao.deleteAll()

        groupDao.upsertAll(payload.groups.map { GroupEntity(it.name, it.sortOrder, it.hidden) })
        widgetDao.upsertAll(payload.widgets.map { it.toEntity() })
        favoriteDao.insertAll(payload.favorites.map { FavoriteEntity(it) })
        notificationDao.upsertAll(singleEnabled(payload.subscriptions).map { it.toEntity() })

        preferencesStore.setTheme(payload.preferences.theme)
        preferencesStore.setLocale(payload.preferences.locale)
    }

    private suspend fun merge(payload: BackupPayload) {
        // Groups: keep existing ones (and their order), append unknown ones.
        var nextGroupOrder = groupDao.nextSortOrder()
        val existingGroups = groupDao.getAll().map { it.name.lowercase() }.toSet()
        payload.groups
            .filter { it.name.lowercase() !in existingGroups }
            .sortedBy { it.sortOrder }
            .forEach { groupDao.insert(GroupEntity(it.name, nextGroupOrder++, it.hidden)) }

        // Widgets: match by stop. Existing cards gain the backup's visible
        // lines; unknown stops are appended after the current cards.
        val existingWidgets = widgetDao.getAll().associateBy { it.stopId }
        var nextWidgetOrder = widgetDao.nextSortOrder()
        payload.widgets.sortedBy { it.sortOrder }.forEach { w ->
            val existing = existingWidgets[w.stopId]
            if (existing != null) {
                val lines = existing.visibleLines.split(",").filter { it.isNotBlank() }
                val mergedLines = (lines + w.visibleLines).distinct()
                widgetDao.update(existing.copy(visibleLines = mergedLines.joinToString(",")))
            } else {
                widgetDao.upsert(w.toEntity().copy(sortOrder = nextWidgetOrder++))
            }
        }

        favoriteDao.insertAll(payload.favorites.map { FavoriteEntity(it) })

        // Subscriptions: one per stop/line/direction. Backup entries win, but
        // never enable more than one subscription overall.
        val anyEnabled = notificationDao.getEnabled().isNotEmpty()
        val incoming = singleEnabled(payload.subscriptions).map { s ->
            if (anyEnabled) s.copy(enabled = false) else s
        }
        incoming.forEach { s ->
            notificationDao.deleteByKey(s.stopId, s.lineId, s.direction)
            notificationDao.upsert(s.toEntity())
        }
    }

    /** The store only allows one active subscription; keep the first and disable the rest. */
    private fun singleEnabled(subs: List<BackupSubscription>): List<BackupSubscription> {
        var seenEnabled = false
        return subs.map { s ->
            if (s.enabled && !seenEnabled) {
                seenEnabled = true
                s
            } else {
                s.copy(enabled = false)
            }
        }
    }

    private fun BackupPayload.summary() = BackupSummary(
        widgets = widgets.size,
        groups = groups.size,
        favorites = favorites.size,
        subscriptions = subscriptions.size,
        exportedAt = exportedAt,
        platform = platform
    )

    private fun WidgetEntity.toBackup() = BackupWidget(
        id = id,
        stopId = stopId,
        stopName = stopName,
        size = size,
        groupName = groupName,
        visibleLines = visibleLines.split(",").filter { it.isNotBlank() },
        hidden = hidden,
        sortOrder = sortOrder
    )

    private fun BackupWidget.toEntity() = WidgetEntity(
        id = id,
        stopId = stopId,
        stopName = stopName,
        size = size,
        groupName = groupName,
        visibleLines = visibleLines.joinToString(","),
        hidden = hidden,
        sortOrder = sortOrder
    )

    private fun NotificationEntity.toBackup() = BackupSubscription(
        id = id,
        stopId = stopId,
        stopName = stopName,
        lineId = lineId,
        lineName = lineName,
        lineColor = lineColor,
        direction = direction,
        mode = mode,
        thresholdMinutes = thresholdMinutes,
        enabled = enabled
    )

    private fun BackupSubscription.toEntity() = NotificationEntity(
        id = id,
        stopId = stopId,
        stopName = stopName,
        lineId = lineId,
        lineName = lineName,
        lineColor = lineColor,
        direction = direction,
        mode = mode,
        thresholdMinutes = thresholdMinutes,
        enabled = enabled
    )
}

/** yyyy-MM-dd from epoch millis, without pulling in a date library. */
fun formatBackupDate(epochMs: Long): String {
    var days = epochMs / 86_400_000L
    var year = 1970
    while (true) {
        val len = if (isLeapYear(year)) 366 else 365
        if (days < len) break
        days -= len
        year++
    }
    val monthLengths = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 0
    while (days >= monthLengths[month]) {
        days -= monthLengths[month]
        month++
    }
    val m = (month + 1).toString().padStart(2, '0')
    val d = (days + 1).toString().padStart(2, '0')
    return "$year-$m-$d"
}

private fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
