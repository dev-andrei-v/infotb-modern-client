package ro.infotb.modern.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_groups")
data class GroupEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
    /** Minutes after midnight the group becomes active; null = always shown. */
    val activeFrom: Int? = null,
    /** Minutes after midnight the group stops being active; null = always shown. */
    val activeTo: Int? = null
)
