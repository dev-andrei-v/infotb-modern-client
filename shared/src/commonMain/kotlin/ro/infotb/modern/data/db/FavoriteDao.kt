package ro.infotb.modern.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT lineId FROM favorites")
    fun observeAllIds(): Flow<List<Long>>

    @Query("SELECT lineId FROM favorites")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE lineId = :lineId)")
    suspend fun isFavorite(lineId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE lineId = :lineId")
    suspend fun delete(lineId: Long)
}
