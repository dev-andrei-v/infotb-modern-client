package ro.infotb.modern.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_groups")
data class GroupEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int = 0,
    val hidden: Boolean = false
)
