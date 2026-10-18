package ro.infotb.modern.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notification_subscriptions")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notification_subscriptions WHERE enabled = 1")
    suspend fun getEnabled(): List<NotificationEntity>

    @Query("SELECT * FROM notification_subscriptions")
    suspend fun getAll(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NotificationEntity)

    @Query("DELETE FROM notification_subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notification_subscriptions WHERE stopId = :stopId AND lineId = :lineId AND direction = :direction")
    suspend fun deleteByKey(stopId: Long, lineId: Long, direction: Int)

    @Query("UPDATE notification_subscriptions SET enabled = 0")
    suspend fun disableAll()

    @Query("DELETE FROM notification_subscriptions")
    suspend fun deleteAll()

    @Update
    suspend fun update(entity: NotificationEntity)
}
