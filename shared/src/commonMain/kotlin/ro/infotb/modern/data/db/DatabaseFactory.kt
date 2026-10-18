package ro.infotb.modern.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.CoroutineDispatcher

expect val ioDispatcher: CoroutineDispatcher

/** v2: groups gained an optional active-hours window. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE dashboard_groups ADD COLUMN activeFrom INTEGER")
        connection.execSQL("ALTER TABLE dashboard_groups ADD COLUMN activeTo INTEGER")
    }
}

fun buildDatabase(builder: RoomDatabase.Builder<StbDatabase>): StbDatabase {
    return builder
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher)
        .build()
}

expect fun createDatabaseBuilder(): RoomDatabase.Builder<StbDatabase>
