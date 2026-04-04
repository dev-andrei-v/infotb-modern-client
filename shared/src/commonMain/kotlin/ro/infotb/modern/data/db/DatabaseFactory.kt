package ro.infotb.modern.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher

expect val ioDispatcher: CoroutineDispatcher

fun buildDatabase(builder: RoomDatabase.Builder<StbDatabase>): StbDatabase {
    return builder
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher)
        .build()
}

expect fun createDatabaseBuilder(): RoomDatabase.Builder<StbDatabase>
