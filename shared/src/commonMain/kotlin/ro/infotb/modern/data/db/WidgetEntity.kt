package ro.infotb.modern.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widgets")
data class WidgetEntity(
    @PrimaryKey val id: String,
    val stopId: Long,
    val stopName: String,
    val size: String,
    val groupName: String = "",
    val visibleLines: String = "",
    val hidden: Boolean = false,
    val sortOrder: Int = 0
)
