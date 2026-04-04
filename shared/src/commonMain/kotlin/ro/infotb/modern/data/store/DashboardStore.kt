package ro.infotb.modern.data.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.infotb.modern.data.db.GroupDao
import ro.infotb.modern.data.db.GroupEntity
import ro.infotb.modern.data.db.WidgetDao
import ro.infotb.modern.data.db.WidgetEntity
import kotlin.random.Random

enum class WidgetSize { MEDIUM, LARGE }

data class WidgetConfig(
    val id: String,
    val stopId: Long,
    val stopName: String,
    val size: WidgetSize,
    val group: String = "",
    val visibleLines: List<String> = emptyList(),
    val hidden: Boolean = false
)

class DashboardStore(
    private val widgetDao: WidgetDao,
    private val groupDao: GroupDao,
    private val scope: CoroutineScope
) {

    val widgets: StateFlow<List<WidgetConfig>> = widgetDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<String>> = groupDao.observeAll()
        .map { entities -> entities.map { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val hiddenGroups: StateFlow<List<String>> = groupDao.observeAll()
        .map { entities -> entities.filter { it.hidden }.map { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun addWidget(stopId: Long, stopName: String, size: WidgetSize, lineKey: String): Boolean {
        scope.launch {
            val existing = widgetDao.findByStopId(stopId)
            if (existing != null) {
                val lines = existing.visibleLines.split(",").filter { it.isNotBlank() }
                if (lines.contains(lineKey)) return@launch
                val updatedLines = (lines + lineKey).joinToString(",")
                widgetDao.update(existing.copy(visibleLines = updatedLines))
            } else {
                val order = widgetDao.nextSortOrder()
                widgetDao.upsert(
                    WidgetEntity(
                        id = generateId(),
                        stopId = stopId,
                        stopName = stopName,
                        size = size.name,
                        groupName = "",
                        visibleLines = lineKey,
                        hidden = false,
                        sortOrder = order
                    )
                )
            }
        }
        return true
    }

    fun removeWidget(id: String) {
        scope.launch { widgetDao.deleteById(id) }
    }

    fun clearWidgets() {
        scope.launch { widgetDao.deleteAll() }
    }

    fun resizeWidget(id: String, size: WidgetSize) {
        scope.launch {
            val all = widgetDao.getAll()
            val widget = all.find { it.id == id } ?: return@launch
            widgetDao.update(widget.copy(size = size.name))
        }
    }

    fun reorderWidgets(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val current = widgetDao.getAll().toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return@launch
            val moved = current.removeAt(fromIndex)
            current.add(toIndex, moved)
            current.forEachIndexed { index, entity ->
                widgetDao.update(entity.copy(sortOrder = index))
            }
        }
    }

    fun toggleWidgetHidden(id: String) {
        scope.launch {
            val all = widgetDao.getAll()
            val widget = all.find { it.id == id } ?: return@launch
            widgetDao.update(widget.copy(hidden = !widget.hidden))
        }
    }

    fun toggleLineVisibility(id: String, lineKey: String) {
        scope.launch {
            val all = widgetDao.getAll()
            val widget = all.find { it.id == id } ?: return@launch
            val lines = widget.visibleLines.split(",").filter { it.isNotBlank() }.toMutableList()
            if (lines.contains(lineKey)) {
                lines.remove(lineKey)
            } else {
                lines.add(lineKey)
            }
            widgetDao.update(widget.copy(visibleLines = lines.joinToString(",")))
        }
    }

    fun setWidgetGroup(id: String, group: String) {
        scope.launch {
            val all = widgetDao.getAll()
            val widget = all.find { it.id == id } ?: return@launch
            widgetDao.update(widget.copy(groupName = group))
        }
    }

    fun toggleGroupHidden(name: String) {
        scope.launch {
            val all = groupDao.getAll()
            val group = all.find { it.name == name } ?: return@launch
            groupDao.update(group.copy(hidden = !group.hidden))
        }
    }

    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val existing = groupDao.getAll()
            if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) return@launch
            val order = groupDao.nextSortOrder()
            groupDao.insert(GroupEntity(name = trimmed, sortOrder = order))
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val existing = groupDao.getAll()
            if (trimmed != oldName && existing.any { it.name.equals(trimmed, ignoreCase = true) }) return@launch
            val old = existing.find { it.name == oldName } ?: return@launch
            groupDao.deleteByName(oldName)
            groupDao.insert(GroupEntity(name = trimmed, sortOrder = old.sortOrder, hidden = old.hidden))
            // Update widgets referencing old group
            val widgets = widgetDao.getAll()
            widgets.filter { it.groupName == oldName }.forEach { widget ->
                widgetDao.update(widget.copy(groupName = trimmed))
            }
        }
    }

    fun removeGroup(name: String) {
        scope.launch {
            groupDao.deleteByName(name)
            val widgets = widgetDao.getAll()
            widgets.filter { it.groupName == name }.forEach { widget ->
                widgetDao.update(widget.copy(groupName = ""))
            }
        }
    }

    fun reorderGroups(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val current = groupDao.getAll().toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return@launch
            val moved = current.removeAt(fromIndex)
            current.add(toIndex, moved)
            current.forEachIndexed { index, entity ->
                groupDao.update(entity.copy(sortOrder = index))
            }
        }
    }

    private fun WidgetEntity.toDomain() = WidgetConfig(
        id = id,
        stopId = stopId,
        stopName = stopName,
        size = try { WidgetSize.valueOf(size) } catch (_: Exception) { WidgetSize.MEDIUM },
        group = groupName,
        visibleLines = visibleLines.split(",").filter { it.isNotBlank() },
        hidden = hidden
    )

    private fun generateId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        return buildString { repeat(21) { append(chars[Random.nextInt(chars.length)]) } }
    }
}
