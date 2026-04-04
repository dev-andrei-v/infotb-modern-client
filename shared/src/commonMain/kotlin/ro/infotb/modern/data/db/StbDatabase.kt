package ro.infotb.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WidgetEntity::class,
        FavoriteEntity::class,
        NotificationEntity::class,
        GroupEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class StbDatabase : RoomDatabase() {
    abstract fun widgetDao(): WidgetDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notificationDao(): NotificationDao
    abstract fun groupDao(): GroupDao
}
