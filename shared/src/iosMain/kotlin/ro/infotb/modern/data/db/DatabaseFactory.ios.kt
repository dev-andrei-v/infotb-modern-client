package ro.infotb.modern.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSHomeDirectory

actual val ioDispatcher = Dispatchers.Default

actual fun createDatabaseBuilder(): RoomDatabase.Builder<StbDatabase> {
    val dbFilePath = "${NSHomeDirectory()}/Documents/stb.db"
    return Room.databaseBuilder<StbDatabase>(
        name = dbFilePath
    )
}
