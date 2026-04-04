package ro.infotb.modern.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import ro.infotb.modern.AndroidContext

actual fun createDatabaseBuilder(): RoomDatabase.Builder<StbDatabase> {
    val context = AndroidContext.appContext
    val dbFile = context.getDatabasePath("stb.db")
    return Room.databaseBuilder<StbDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
}
