package ro.infotb.modern.data.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.infotb.modern.data.db.NotificationDao
import ro.infotb.modern.data.db.NotificationEntity
import ro.infotb.modern.data.model.Direction
import kotlin.random.Random

enum class NotificationMode { ONCE, REPEAT }

data class NotificationSubscription(
    val id: String,
    val stopId: Long,
    val stopName: String,
    val lineId: Long,
    val lineName: String,
    val lineColor: String,
    val direction: Direction,
    val mode: NotificationMode,
    val thresholdMinutes: Int,
    val enabled: Boolean
)

class NotificationStore(
    private val dao: NotificationDao,
    private val scope: CoroutineScope
) {

    val subscriptions: StateFlow<List<NotificationSubscription>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun addSubscription(
        stopId: Long,
        stopName: String,
        lineId: Long,
        lineName: String,
        lineColor: String,
        direction: Direction,
        mode: NotificationMode,
        thresholdMinutes: Int
    ) {
        scope.launch {
            dao.disableAll()
            dao.deleteByKey(stopId, lineId, direction.value)
            dao.upsert(
                NotificationEntity(
                    id = generateId(),
                    stopId = stopId,
                    stopName = stopName,
                    lineId = lineId,
                    lineName = lineName,
                    lineColor = lineColor,
                    direction = direction.value,
                    mode = mode.name,
                    thresholdMinutes = thresholdMinutes,
                    enabled = true
                )
            )
        }
    }

    fun removeSubscription(id: String) {
        scope.launch { dao.deleteById(id) }
    }

    fun toggleSubscription(id: String) {
        scope.launch {
            val current = subscriptions.value.find { it.id == id } ?: return@launch
            val enabling = !current.enabled
            if (enabling) {
                dao.disableAll()
            }
            dao.update(current.toEntity().copy(enabled = enabling))
        }
    }

    private fun NotificationEntity.toDomain() = NotificationSubscription(
        id = id,
        stopId = stopId,
        stopName = stopName,
        lineId = lineId,
        lineName = lineName,
        lineColor = lineColor,
        direction = Direction.fromInt(direction),
        mode = NotificationMode.valueOf(mode),
        thresholdMinutes = thresholdMinutes,
        enabled = enabled
    )

    private fun NotificationSubscription.toEntity() = NotificationEntity(
        id = id,
        stopId = stopId,
        stopName = stopName,
        lineId = lineId,
        lineName = lineName,
        lineColor = lineColor,
        direction = direction.value,
        mode = mode.name,
        thresholdMinutes = thresholdMinutes,
        enabled = enabled
    )

    private fun generateId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        return buildString { repeat(21) { append(chars[Random.nextInt(chars.length)]) } }
    }
}
