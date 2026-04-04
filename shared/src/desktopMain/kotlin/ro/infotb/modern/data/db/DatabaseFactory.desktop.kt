package ro.infotb.modern.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import java.io.File

actual val ioDispatcher = Dispatchers.IO

actual fun createDatabaseBuilder(): RoomDatabase.Builder<StbDatabase> {
    val dbDir = File(System.getProperty("user.home"), ".stb")
    dbDir.mkdirs()
    val dbFile = File(dbDir, "stb.db")
    return Room.databaseBuilder<StbDatabase>(
        name = dbFile.absolutePath
    )
}
