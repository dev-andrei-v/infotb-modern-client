package ro.infotb.modern.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {

    @Query("SELECT * FROM widgets ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<WidgetEntity>>

    @Query("SELECT * FROM widgets ORDER BY sortOrder ASC")
    suspend fun getAll(): List<WidgetEntity>

    @Query("SELECT * FROM widgets WHERE stopId = :stopId LIMIT 1")
    suspend fun findByStopId(stopId: Long): WidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(widget: WidgetEntity)

    @Update
    suspend fun update(widget: WidgetEntity)

    @Query("DELETE FROM widgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM widgets")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM widgets")
    suspend fun nextSortOrder(): Int
}
