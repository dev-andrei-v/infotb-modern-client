package ro.infotb.modern.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_subscriptions")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val stopId: Long,
    val stopName: String,
    val lineId: Long,
    val lineName: String,
    val lineColor: String,
    val direction: Int,
    val mode: String,
    val thresholdMinutes: Int,
    val enabled: Boolean = true
)
