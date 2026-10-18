package ro.infotb.modern.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        WidgetEntity::class,
        FavoriteEntity::class,
        NotificationEntity::class,
        GroupEntity::class
    ],
    version = 2,
    exportSchema = true
)
@ConstructedBy(StbDatabaseConstructor::class)
abstract class StbDatabase : RoomDatabase() {
    abstract fun widgetDao(): WidgetDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notificationDao(): NotificationDao
    abstract fun groupDao(): GroupDao
}

// Room generates the implementation automatically
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object StbDatabaseConstructor : RoomDatabaseConstructor<StbDatabase>
